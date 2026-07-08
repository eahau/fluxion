package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.ExecutionsApi
import com.fluxion.admin.generated.model.*
import com.fluxion.admin.mapper.DebugMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.admin.service.WfExecutionSnapshotService
import com.fluxion.core.debug.DebugService
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.signal.Signal
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import kotlinx.coroutines.runBlocking
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Workflow execution history, re-run, and signal-delivery REST controller
 * (implements OpenAPI-generated [ExecutionsApi]).
 *
 * Covers three execution-centric concerns:
 *   1. **List / inspect** past executions stored in `wf_execution_snapshot`
 *      (keyword + status + workflowId filters applied in-memory after page fetch).
 *   2. **Re-run** either a full execution (replay inputs against the latest def)
 *      or a single-node partial rerun (uses DebugService.rerunFromNode to splice
 *      nodeOutputs from the old execution upstream of the rerun start).
 *   3. **Signal API** — send a named signal payload to a running (wait-node-blocked)
 *      execution, or list the pending / waiting signals for a given execution id.
 *      Requires an optional [SignalBroker] bean; when missing the API gracefully
 *      falls back to 503 / empty lists (admin UI can still show non-signal features).
 *
 * Collaborates with: WfExecutionSnapshotService (snapshot persistence + SPI for
 * DebugService), WfDefinitionService (load defs for reruns), DebugService
 * (rerunFromNode), DagExecutor (full rerun), DebugMapper (NodeTrace rendering),
 * SignalBroker (optional — wait-node / pending-signal queries).
 */
@RestController
class ExecutionsController(
    private val snapshotService: WfExecutionSnapshotService,
    private val definitionService: WfDefinitionService,
    private val debugService: DebugService,
    private val dagExecutor: DagExecutor,
    private val debugMapper: DebugMapper,
    private val signalBroker: SignalBroker? = null
) : ExecutionsApi {

    /**
     * Paginated execution listing.
     *
     * Filtering is done in-memory after the page fetch (good balance given that
     * snapshots are append-only and paged by newest-first: users typically browse
     * the first 2-3 pages only). Keyword matches executionId OR workflowId; status
     * matches SUCCESS / FAILED; workflowId is an exact match on the FK column.
     */
    override fun listExecutions(
        keyword: String?,
        status: String?,
        workflowId: String?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseExecutionRecord> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val all = snapshotService.findAll(pageable)
        val filtered = all.content.filter { snap ->
            val matchKeyword = keyword.isNullOrBlank() ||
                snap.executionId.contains(keyword, ignoreCase = true) ||
                snap.workflowId.contains(keyword, ignoreCase = true)
            val matchStatus = status.isNullOrBlank() ||
                (status.equals("SUCCESS", ignoreCase = true) && snap.success) ||
                (status.equals("FAILED", ignoreCase = true) && !snap.success)
            val matchWorkflow = workflowId.isNullOrBlank() || snap.workflowId == workflowId
            matchKeyword && matchStatus && matchWorkflow
        }

        return ResponseEntity.ok(PageResponseExecutionRecord().apply {
            total = all.totalElements
            this.page = all.number
            this.pageSize = size
            list = filtered.map { toExecutionRecord(it) }.toMutableList()
        })
    }

    /** Fetch a single execution detail (trace + inputs + node outputs) by execution id. */
    override fun getExecution(executionId: String): ResponseEntity<TraceDetail> {
        val snap = snapshotService.findByExecutionIdRaw(executionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toTraceDetail(snap))
    }

    /**
     * Rerun a previous execution.
     *
     * Two modes:
     *   - `nodeId` blank = **full rerun** — load original inputs from snapshot and
     *     execute the current (ACTIVE) version of the workflow definition from scratch.
     *   - `nodeId` present = **partial rerun** — delegated to DebugService which
     *     rebuilds upstream state from the original snapshot and only (re)executes
     *     downstream nodes.
     */
    override fun rerunExecution(
        executionId: String,
        rerunExecutionRequest: RerunExecutionRequest?
    ): ResponseEntity<TraceDetail> {
        val nodeId = rerunExecutionRequest?.nodeId
        return if (nodeId.isNullOrBlank()) {
            val snap = snapshotService.findByExecutionIdRaw(executionId)
                ?: return ResponseEntity.notFound().build()
            val def = definitionService.get(snap.workflowId)
                ?: return ResponseEntity.notFound().build()
            val inputs = snap.inputsJson?.let { JsonUtil.toMap(it) } ?: emptyMap()
            val rerunResult = runBlocking {
                dagExecutor.execute(def, inputs)
            }
            snapshotService.save(rerunResult, def.id, def.version)
            ResponseEntity.ok(toTraceDetail(executionId, rerunResult, def.name))
        } else {
            val result = runBlocking {
                debugService.rerunFromNode(executionId, nodeId, null)
            }
            ResponseEntity.ok(toTraceDetail(executionId, result))
        }
    }

    // ─── Trace DTO helpers ───────────────────────────────────────────

    /** EngineResult → TraceDetail (convenience overload for full reruns). */
    private fun toTraceDetail(executionId: String, result: com.fluxion.core.value.EngineResult, workflowName: String = ""): TraceDetail =
        toTraceDetail(executionId, workflowName, result.trace)

    /** RerunResult → TraceDetail convenience overload. */
    private fun toTraceDetail(executionId: String, result: com.fluxion.core.value.RerunResult): TraceDetail =
        toTraceDetail(executionId, "", result.trace)

    /** Shared base: NodeExecutionRecord trace list → TraceDetail. */
    private fun toTraceDetail(
        executionId: String,
        workflowName: String,
        trace: List<com.fluxion.core.value.NodeExecutionRecord>
    ): TraceDetail = TraceDetail().apply {
        this.executionId = executionId
        this.workflowName = workflowName
        totalDurationMs = trace.sumOf { it.durationMs }
        status = if (trace.none { it.status.name == "FAILED" }) "SUCCESS" else "FAILED"
        traces = trace.map { toTraceNode(debugMapper.toNodeTrace(it)) }.toMutableList()
    }

    /** generated.NodeTrace (DebugMapper) → generated.TraceNode for the UI panel. */
    private fun toTraceNode(nodeTrace: NodeTrace): TraceNode = TraceNode().apply {
        nodeId = nodeTrace.nodeId
        nodeName = nodeTrace.nodeName
        durationMs = nodeTrace.durationMs?.toLong()
        status = nodeTrace.status?.value
        inputs = nodeTrace.inputs?.toMutableMap() ?: mutableMapOf()
        output = nodeTrace.output?.toMutableMap() ?: mutableMapOf()
        error = nodeTrace.error
    }

    /** Snapshot row → ExecutionRecord summary for the list page. */
    private fun toExecutionRecord(snap: com.fluxion.admin.entity.WfExecutionSnapshot): ExecutionRecord {
        val def = definitionService.getEntity(snap.workflowId)
        return ExecutionRecord().apply {
            this.executionId = snap.executionId
            this.workflowId = snap.workflowId
            workflowName = def?.workflowName ?: ""
            this.status = if (snap.success) "SUCCESS" else "FAILED"
            totalDurationMs = 0
            startTime = snap.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            endTime = snap.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    /** Snapshot row → full TraceDetail for the detail view. */
    private fun toTraceDetail(snap: com.fluxion.admin.entity.WfExecutionSnapshot): TraceDetail {
        val def = definitionService.getEntity(snap.workflowId)
        val nodeTraces = snap.traceJson?.let {
            JsonUtil.deserializeList(it, com.fluxion.core.value.NodeExecutionRecord::class.java)
        } ?: emptyList()
        return TraceDetail().apply {
            executionId = snap.executionId
            workflowName = def?.workflowName ?: ""
            totalDurationMs = nodeTraces.sumOf { it.durationMs }
            status = if (snap.success) "SUCCESS" else "FAILED"
            traces = nodeTraces.map { record ->
                TraceNode().apply {
                    nodeId = record.nodeId
                    nodeName = record.nodeName
                    durationMs = record.durationMs
                    this.status = record.status.name
                    inputs = record.input.uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
                    output = record.output.uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
                    error = record.error?.message
                }
            }.toMutableList()
        }
    }

    // ─── Signal delivery API (wait-node resume) ────────────────────

    /**
     * Deliver a signal to a waiting execution. If no [SignalBroker] is configured
     * in the Spring context we return 503 with a BUFFERED status so the UI can
     * display the correct affordance (admin JVM is running in no-signal mode).
     * Otherwise status mirrors what the broker reports: DELIVERED = a wait node
     * picked up the signal immediately; BUFFERED = execution hasn't reached the
     * wait node yet (signal lives on the pending queue until it does).
     */
    @PostMapping("/api/admin/executions/{executionId}/signal")
    override fun sendSignal(
        @PathVariable executionId: String,
        @RequestBody request: com.fluxion.admin.generated.model.SendSignalRequest
    ): ResponseEntity<com.fluxion.admin.generated.model.SendSignal200Response> {
        val broker = signalBroker
            ?: return ResponseEntity.status(503).body(
                com.fluxion.admin.generated.model.SendSignal200Response().apply {
                    this.executionId = executionId
                    this.signalName = request.signalName
                    this.status = com.fluxion.admin.generated.model.SendSignal200Response.Status.BUFFERED
                }
            )

        val signal = Signal(
            name = request.signalName,
            payload = request.data
        )
        val accepted = broker.send(executionId, signal)

        return ResponseEntity.ok(
            com.fluxion.admin.generated.model.SendSignal200Response().apply {
                this.executionId = executionId
                this.signalName = request.signalName
                this.status = if (accepted) {
                    com.fluxion.admin.generated.model.SendSignal200Response.Status.DELIVERED
                } else {
                    com.fluxion.admin.generated.model.SendSignal200Response.Status.BUFFERED
                }
            }
        )
    }

    /**
     * Query a running execution for two signal queues:
     *   - `waiting`  — signal names the execution is currently blocked on (wait nodes)
     *   - `buffered` — signals already delivered but not yet consumed (in transit)
     *
     * Returns empty lists when no SignalBroker is present in the application context.
     */
    @GetMapping("/api/admin/executions/{executionId}/signals/waiting")
    override fun getWaitingSignals(@PathVariable executionId: String): ResponseEntity<com.fluxion.admin.generated.model.GetWaitingSignals200Response> {
        val broker = signalBroker
            ?: return ResponseEntity.ok(
                com.fluxion.admin.generated.model.GetWaitingSignals200Response().apply {
                    this.executionId = executionId
                    this.waiting = mutableListOf()
                    this.buffered = mutableListOf()
                }
            )

        return ResponseEntity.ok(
            com.fluxion.admin.generated.model.GetWaitingSignals200Response().apply {
                this.executionId = executionId
                this.waiting = broker.waitingSignals(executionId).toMutableList()
                this.buffered = broker.pendingSignals(executionId).toMutableList()
            }
        )
    }
}
