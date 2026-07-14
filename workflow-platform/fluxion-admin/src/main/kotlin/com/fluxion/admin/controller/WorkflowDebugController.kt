package com.fluxion.admin.controller

import com.fluxion.admin.exception.WorkflowAdminException
import com.fluxion.admin.generated.api.DebugApi
import com.fluxion.admin.generated.model.*
import com.fluxion.admin.mapper.DebugMapper
import com.fluxion.admin.mapper.MockConfigMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.admin.service.WfExecutionSnapshotService
import com.fluxion.core.debug.DebugService
import com.fluxion.core.debug.DebugSnapshot
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import kotlinx.coroutines.runBlocking
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * REST API for the workflow designer's in-browser debugger.
 *
 * Implements the generated OpenAPI `DebugApi` surface. Endpoints run against a
 * transient in-memory engine instance so nothing is published to workers and no
 * real side-effects (HTTP, DB writes, etc.) are executed; mock fixtures supplied
 * by the caller are injected via `MockConfig` instead.
 */
@RestController
class WorkflowDebugController(
    private val debugService: DebugService,
    private val snapshotService: WfExecutionSnapshotService,
    private val definitionService: WfDefinitionService,
    private val dagExecutor: DagExecutor,
    private val workflowEngine: WorkflowEngine,
    private val debugMapper: DebugMapper
) : DebugApi {

    override fun debugWorkflow(
        workflowId: String,
        debugWorkflowRequest: DebugWorkflowRequest
    ): ResponseEntity<DebugResult> {
        val def = definitionService.getDefinitionAnyStatus(workflowId)
            ?: throw WorkflowAdminException.notFound("WORKFLOW_NOT_FOUND", "Workflow not found: $workflowId")

        val mockConfig = MockConfigMapper.toCoreMockConfig(debugWorkflowRequest.mockConfig)
        val result = runBlocking {
            dagExecutor.execute(def, debugWorkflowRequest.inputs ?: emptyMap(), )
        }
        snapshotService.save(result, def.id, def.version)

        val snapshot = debugService.createSnapshot(
            result, def.id, def.version,
            debugWorkflowRequest.breakpoints?.toSet(),
            mockConfig
        )
        return ResponseEntity.ok(toDebugResult(snapshot))
    }

    override fun debugWorkflowNode(
        workflowId: String,
        debugNodeRequest: DebugNodeRequest
    ): ResponseEntity<DebugResult> {
        val def = definitionService.getDefinitionAnyStatus(workflowId)
            ?: throw WorkflowAdminException.notFound("WORKFLOW_NOT_FOUND", "Workflow not found: $workflowId")

        val node = def.nodes.find { it.id == debugNodeRequest.nodeId }
            ?: throw WorkflowAdminException.badRequest("NODE_NOT_FOUND", "Node not found: ${debugNodeRequest.nodeId}")

        val input = debugNodeRequest.inputData ?: emptyMap()
        var state = ImmutableExecutionState.start(def, input)
        // Single-node debugging: synthesize empty outputs for every declared upstream
        // dependency so the engine does not throw DependencyNotReadyException before
        // the chosen node can even start.
        node.dependsOn?.forEach { depId ->
            if (!state.hasNodeOutput(depId)) {
                state = state.withNodeOutput(depId, emptyMap<String, Any>())
            }
        }
        val mockConfig = MockConfigMapper.toCoreMockConfig(debugNodeRequest.mockConfig)
        val record = runBlocking {
            workflowEngine.executeNode(node, input, state,)
        }

        return ResponseEntity.ok(
            DebugResult().apply {
                status = if (record.status.name == "FAILED") DebugStatus.FAILED else DebugStatus.COMPLETED
                finalOutput = record.output.uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
                totalDurationMs = record.durationMs.toInt()
                traces = mutableListOf(debugMapper.toNodeTrace(record))
            }
        )
    }

    override fun debugWorkflowStep(
        workflowId: String,
        debugStepRequest: DebugStepRequest
    ): ResponseEntity<DebugResult> {
        val snapshot = try {
            JsonUtil.convertValue(debugStepRequest.snapshot, DebugSnapshot::class.java)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("SNAPSHOT_INVALID", "Failed to parse debug snapshot: ${ex.message}")
        }
        val nextSnapshot = runBlocking { debugService.step(snapshot) }
        return ResponseEntity.ok(toDebugResult(nextSnapshot))
    }

    override fun rerunWorkflowNode(
        workflowId: String,
        debugRerunRequest: DebugRerunRequest
    ): ResponseEntity<DebugResult> {
        val snapshot = try {
            JsonUtil.convertValue(debugRerunRequest.snapshot, DebugSnapshot::class.java)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("SNAPSHOT_INVALID", "Failed to parse debug snapshot: ${ex.message}")
        }
        val executionId = snapshot.executionState.meta.executionId
            ?: throw WorkflowAdminException.badRequest("EXECUTION_ID_MISSING", "Execution ID missing from snapshot")
        val result = runBlocking {
            debugService.rerunFromNode(
                executionId,
                debugRerunRequest.nodeId,
                debugRerunRequest.overrideInput
            )
        }
        return ResponseEntity.ok(debugMapper.toDebugResult(result))
    }

    override fun listDebugHistory(
        workflowId: String,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseDebugExecutionRecord> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = snapshotService.findSnapshots(workflowId, pageable)

        return ResponseEntity.ok(PageResponseDebugExecutionRecord().apply {
            list = result.content.map { debugMapper.toDebugExecutionRecord(it) }.toMutableList()
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
        })
    }

    override fun getDebugHistoryDetail(
        workflowId: String,
        executionId: String
    ): ResponseEntity<DebugExecutionRecord> {
        val snap = snapshotService.findByExecutionIdRaw(executionId)
            ?: return ResponseEntity.notFound().build()

        if (snap.workflowId != workflowId) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(debugMapper.toDebugExecutionRecord(snap).apply {
            inputs = snap.inputsJson?.let { JsonUtil.toMap(it) }?.toMutableMap() ?: mutableMapOf()
            nodeTraces = snap.traceJson?.let {
                JsonUtil.deserializeList<NodeTrace>(it, NodeTrace::class.java)
            }?.toMutableList() ?: mutableListOf()
            finalOutput = snap.nodeOutputsJson?.let { JsonUtil.toMap(it) }?.toMutableMap() ?: mutableMapOf()
        })
    }

    private fun toDebugResult(snapshot: DebugSnapshot): DebugResult =
        DebugResult().apply {
            status = if (snapshot.pausedAtNodeId != null) DebugStatus.PAUSED else DebugStatus.COMPLETED
            finalOutput = snapshot.executionState.nodeOutputs.values.lastOrNull().uncheckedCast<Map<String, Any>>()?.toMutableMap()
                ?: mutableMapOf()
            totalDurationMs = snapshot.traces.sumOf { it.durationMs }.toInt()
            traces = snapshot.traces.map { debugMapper.toNodeTrace(it) }.toMutableList()
            this.snapshot = mutableMapOf<String, Any>(
                "workflowId" to snapshot.workflowId,
                "workflowVersion" to snapshot.workflowVersion,
                "nextNodeIndex" to snapshot.nextNodeIndex,
                "pausedAtNodeId" to (snapshot.pausedAtNodeId ?: ""),
                "nodeOutputs" to snapshot.executionState.nodeOutputs,
                "inputs" to snapshot.executionState.inputs,
                "mockConfig" to JsonUtil.toMap(JsonUtil.serialize(snapshot.mockConfig))
            )
        }
}
