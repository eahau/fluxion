package com.fluxion.admin.controller

import com.fluxion.admin.security.DebugSecurityGuard
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.admin.service.WfExecutionSnapshotService
import com.fluxion.core.debug.DebugService
import com.fluxion.core.debug.DebugSnapshot
import com.fluxion.core.engine.ExecutionTracker
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockRule
import com.fluxion.core.signal.Signal
import com.fluxion.core.signal.SignalBroker
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.fluxion.core.engine.DagExecutor

/**
 * Workflow debugger REST controller — stateless (no server-side debug sessions).
 *
 * Debug contract (the client holds the entire DebugSnapshot as state and passes it
 * on every request):
 *   - `POST /debug/start`      → execute the full workflow, return a DebugSnapshot
 *   - `POST /debug/step`       → advance one node
 *   - `POST /debug/continue`   → advance to the next breakpoint or end
 *   - `POST /debug/mock`       → inject a MockRule into an existing DebugSnapshot
 *   - `POST /executions/{id}/rerun` → replay from a specific node id
 *
 * Every mutating endpoint wraps its work in [DebugSecurityGuard] try/finally which
 * rate-limits one active debug session per JVM / per-client token to avoid resource
 * exhaustion from rogue UI tabs.
 *
 * Companion execution-observation endpoints:
 *   - `GET /executions/{id}/query`   → current status (RUNNING / COMPLETED / FAILED)
 *   - `GET /executions/running`      → list of in-flight executions
 *   - `GET /executions`              → paginated snapshot listing
 *   - `GET /executions/{id}`         → snapshot detail
 *   - `POST /executions/{id}/signals`→ send a signal to a waiting execution
 *
 * Collaborates with: DebugService (pure debug state machine — no Spring dependency),
 * DagExecutor (initial execution for debug/start), ExecutionTracker (running execs),
 * SignalBroker (signal delivery to wait nodes), DebugSecurityGuard (concurrency),
 * WfDefinitionService (load definitions by id), WfExecutionSnapshotService (snapshots).
 */
@RestController
@RequestMapping("/api/admin")
class DebugController(
    private val debugService: DebugService,
    private val snapshotService: WfExecutionSnapshotService,
    private val definitionService: WfDefinitionService,
    private val securityGuard: DebugSecurityGuard,
    private val dagExecutor: DagExecutor,
    private val signalBroker: SignalBroker,
    private val executionTracker: ExecutionTracker
) {

    // ─── Debug Session endpoints ────────────────────────────────────

    /**
     * Start a debugging session by running the workflow from scratch.
     *
     * Acquires the single-instance debug lock via [DebugSecurityGuard], executes the
     * workflow via [DagExecutor], persists a snapshot for later re-inspection, then
     * hands the EngineResult to DebugService.createSnapshot which prepares the first
     * paused-at-head state. Breakpoints (node ids) can be pre-seeded so the UI can
     * immediately call /continue to jump to the first interesting node.
     */
    @PostMapping("/debug/start")
    suspend fun debugStart(
        @RequestBody req: DebugStartRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val def = definitionService.get(req.workflowId)
                ?: return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Workflow not found: ${req.workflowId}"))

            val result = dagExecutor.execute(def, req.params ?: emptyMap())

            snapshotService.save(result, def.id, def.version)

            val snap = debugService.createSnapshot(
                result, def.id, def.version, req.breakpoints?.toSet()
            )
            ResponseEntity.ok(ApiResponse.ok(snap))
        } finally {
            securityGuard.release()
        }
    }

    /** Step forward one node (client passes the full DebugSnapshot). */
    @PostMapping("/debug/step")
    suspend fun debugStep(
        @RequestBody snap: DebugSnapshot,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            ResponseEntity.ok(ApiResponse.ok(debugService.step(snap)))
        } finally {
            securityGuard.release()
        }
    }

    /** Continue execution from the current paused node until a breakpoint or terminal state. */
    @PostMapping("/debug/continue")
    suspend fun debugContinue(
        @RequestBody snap: DebugSnapshot,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            ResponseEntity.ok(ApiResponse.ok(debugService.continueToBreakpoint(snap)))
        } finally {
            securityGuard.release()
        }
    }

    /**
     * Inject a [MockRule] into an existing snapshot and return a new snapshot.
     *
     * Uses the round-trip pattern: deserialize snapshot → mutate mock context →
     * serialize back. All existing executed-node outputs are preserved so the user
     * does not have to re-run the workflow from scratch just to add a mock.
     */
    @PostMapping("/debug/mock")
    fun debugMock(
        @RequestBody req: MockRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<DebugSnapshot>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val snap = req.snapshot
            val def = definitionService.get(snap.workflowId)
                ?: return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Workflow not found: ${snap.workflowId}"))

            val ctx = DebugSnapshot.restore(snap, def)
            ctx.addMockRule(req.rule)
            val newSnap = ctx.toSnapshot(snap.workflowId, snap.workflowVersion, snap.pausedAtNodeId)
            ResponseEntity.ok(ApiResponse.ok(newSnap))
        } finally {
            securityGuard.release()
        }
    }

    // ─── Rerun from node ───────────────────────────────────────────

    /**
     * Partial rerun: starting from [RerunRequest.nodeId]. All upstream node outputs
     * are loaded from the original snapshot. Useful for fixing a script function
     * and re-running only the downstream nodes without the full workflow cost.
     */
    @PostMapping("/executions/{executionId}/rerun")
    suspend fun rerun(
        @PathVariable executionId: String,
        @RequestBody req: RerunRequest,
        httpReq: HttpServletRequest
    ): ResponseEntity<ApiResponse<RerunResultDto>> {
        securityGuard.checkAndAcquire(httpReq)
        return try {
            val result = debugService.rerunFromNode(executionId, req.nodeId, req.overrideInput)
            ResponseEntity.ok(ApiResponse.ok(
                RerunResultDto(
                    nodeCount  = result.trace.size,
                    success    = result.trace.none { it.status.name == "FAILED" },
                    traceIds   = result.trace.map { it.nodeId }
                )
            ))
        } finally {
            securityGuard.release()
        }
    }

    // ─── Signal / Query endpoints ──────────────────────────────────

    /**
     * Send an ad-hoc signal to a running execution (simpler version of the
     * generated ExecutionsApi.sendSignal — different payload shape).
     *
     * Returns the delivery status as a structured response instead of the
     * generated 3-level enum model.
     */
    @PostMapping("/executions/{executionId}/signals")
    fun sendSignal(
        @PathVariable executionId: String,
        @RequestBody req: SignalRequest
    ): ResponseEntity<ApiResponse<Any>> {
        val delivered = signalBroker.send(executionId, Signal(req.signalName, req.payload))
        return if (delivered) {
            ResponseEntity.ok(ApiResponse.ok(mapOf("status" to "delivered")))
        } else {
            ResponseEntity.ok(ApiResponse.error("Execution not found: $executionId"))
        }
    }

    /**
     * Combined "what's the status of this execution" query — checks live running
     * state first (via [ExecutionTracker]), falls back to persisted snapshot when
     * the execution has already completed. Running executions additionally include
     * signal queues so the user can wait for approvals.
     */
    @GetMapping("/executions/{executionId}/query")
    fun queryExecution(
        @PathVariable executionId: String
    ): ResponseEntity<ApiResponse<Any>> {
        val running = executionTracker.getRunning(executionId)
        if (running == null) {
            val snap = snapshotService.findByExecutionIdRaw(executionId)
                ?: return ResponseEntity.notFound().build()
            return ResponseEntity.ok(ApiResponse.ok(mapOf(
                "executionId" to executionId,
                "status" to if (snap.success) "COMPLETED" else "FAILED",
                "workflowId" to snap.workflowId,
                "workflowVersion" to snap.workflowVersion,
                "startTime" to snap.createdAt,
                "completedNodeIds" to (snap.nodeOutputsJson?.let {
                    try { com.fluxion.core.util.JsonUtil.toMap(it).keys.toList() } catch (_: Exception) { emptyList<String>() }
                } ?: emptyList<String>()),
                "inputs" to (snap.inputsJson?.let {
                    try { com.fluxion.core.util.JsonUtil.toMap(it) } catch (_: Exception) { emptyMap<String, Any>() }
                } ?: emptyMap<String, Any>())
            )))
        }
        return ResponseEntity.ok(ApiResponse.ok(mapOf(
            "executionId" to executionId,
            "status" to "RUNNING",
            "workflowId" to running.workflowId,
            "workflowName" to running.workflowName,
            "workflowVersion" to running.version,
            "startTime" to running.startTime,
            "inputs" to running.inputs,
            "nodeIds" to running.nodeIds,
            "waitingSignals" to signalBroker.waitingSignals(executionId),
            "bufferedSignals" to signalBroker.pendingSignals(executionId)
        )))
    }

    /**
     * List every currently running execution in this JVM (plus its signal queue state).
     * Useful for the admin "activity monitor" sidebar.
     */
    @GetMapping("/executions/running")
    fun listRunningExecutions(): ResponseEntity<ApiResponse<Any>> {
        val running = executionTracker.listRunning()
        return ResponseEntity.ok(ApiResponse.ok(running.map { mapOf(
            "executionId" to it.executionId,
            "workflowId" to it.workflowId,
            "workflowName" to it.workflowName,
            "version" to it.version,
            "startTime" to it.startTime,
            "waitingSignals" to signalBroker.waitingSignals(it.executionId)
        )}))
    }

    // ─── Execution snapshot list / detail (hand-written variant) ───

    /** Paginated snapshot list (compatible with the debug UI sidebar). */
    @GetMapping("/executions")
    fun listExecutions(
        @RequestParam(required = false) workflowId: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Any>> {
        val pageable = PageRequest.of(page, size.coerceAtMost(100),
            Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = if (workflowId.isNullOrBlank()) {
            snapshotService.findAll(pageable)
        } else {
            snapshotService.findSnapshots(workflowId, pageable)
        }
        return ResponseEntity.ok(ApiResponse.ok(mapOf(
            "total"   to result.totalElements,
            "page"    to result.number,
            "records" to result.content.map { it.toSummaryDto() }
        )))
    }

    /** One snapshot detail (full JSON payloads) for the trace inspector. */
    @GetMapping("/executions/{executionId}")
    fun getExecution(
        @PathVariable executionId: String
    ): ResponseEntity<ApiResponse<Any>> {
        val snap = snapshotService.findByExecutionIdRaw(executionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ApiResponse.ok(snap.toDetailDto()))
    }

    // ─── Inline Request / Response DTOs ────────────────────────────

    /** Debug session starter payload. */
    data class DebugStartRequest(
        val workflowId: String,
        val params: Map<String, Any>? = null,
        val breakpoints: List<String>? = null
    )

    /** Apply a mock rule to an existing debug snapshot. */
    data class MockRequest(
        val snapshot: DebugSnapshot,
        val rule: MockRule
    )

    /** Node-level rerun payload (optionally override the node's input value). */
    data class RerunRequest(
        val nodeId: String,
        val overrideInput: Any? = null
    )

    /** Node rerun summary DTO returned to the UI. */
    data class RerunResultDto(
        val nodeCount: Int,
        val success: Boolean,
        val traceIds: List<String>
    )

    /** Generic signal delivery payload. */
    data class SignalRequest(
        val signalName: String,
        val payload: Any? = null
    )

    /**
     * Standard envelope for every hand-written endpoint response. Ensures uniform
     * `{ success, data | error }` shape the debug UI relies on.
     */
    data class ApiResponse<T>(
        val success: Boolean,
        val data: T? = null,
        val error: String? = null
    ) {
        companion object {
            fun <T> ok(data: T) = ApiResponse(true, data)
            fun <T> error(msg: String) = ApiResponse<T>(false, error = msg)
        }
    }

    // ─── Snapshot entity → DTO extension helpers ────────────────────

    /** Minimal summary fields used by the list page (no large JSON blobs). */
    private fun com.fluxion.admin.entity.WfExecutionSnapshot.toSummaryDto() = mapOf(
        "executionId"     to executionId,
        "workflowId"      to workflowId,
        "workflowVersion" to workflowVersion,
        "success"         to success,
        "errorMsg"        to errorMsg,
        "createdAt"       to createdAt
    )

    /** Detail view payload — includes the raw JSON inputs / nodeOutputs / trace strings. */
    private fun com.fluxion.admin.entity.WfExecutionSnapshot.toDetailDto() = mapOf(
        "executionId"     to executionId,
        "workflowId"      to workflowId,
        "workflowVersion" to workflowVersion,
        "success"         to success,
        "inputsJson"      to inputsJson,
        "nodeOutputsJson" to nodeOutputsJson,
        "traceJson"       to traceJson,
        "errorMsg"        to errorMsg,
        "createdAt"       to createdAt
    )
}
