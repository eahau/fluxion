package com.fluxion.admin.service

import com.fluxion.admin.entity.WfExecutionSnapshot
import com.fluxion.admin.repository.WfExecutionSnapshotRepository
import com.fluxion.core.debug.DebugService
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.EngineResult
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import org.slf4j.*
import org.slf4j.debug
import org.slf4j.warn
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Persist and replay workflow execution snapshots.
 *
 * Implements three SPIs that let the admin UI reconstruct a full EngineResult for
 * any historical run without the runtime having to ship its entire in-memory
 * execution state:
 *   - [ExecutionSnapshotStore]: called after every workflow execution, persists
 *     (executionId → inputs + nodeOutputs + trace + errorMsg) into wf_execution_snapshot
 *   - [DebugService.ExecutionLogRepository]: reconstructs [EngineResult] for the
 *     debug step-through UI by loading the snapshot + definition then replaying
 *     the stored nodeOutputs into a fresh [ImmutableExecutionState]
 *   - [DebugService.DefinitionLoader]: resolves [WorkflowDefinition] by id so the
 *     debugger has node metadata to render alongside outputs
 *
 * Collaborates with: WfExecutionSnapshotRepository (JPA persistence),
 * WfDefinitionService (definition loader delegation).
 */
@Service
class WfExecutionSnapshotService(
    private val repository: WfExecutionSnapshotRepository,
    private val definitionService: WfDefinitionService
) : DebugService.ExecutionLogRepository, DebugService.DefinitionLoader, ExecutionSnapshotStore {

    private val log = LoggerFactory.getLogger(javaClass)

    // ===== Persist (ExecutionSnapshotStore SPI) =====

    /**
     * Persist one [EngineResult] row. Idempotent: if the executionId already exists
     * in the table (common in retried async executions) we skip to avoid a unique
     * key violation. Also skips results with no executionId at all (synchronous
     * anonymous invocations that the caller does not want traced).
     */
    override fun save(result: EngineResult, workflowId: String, version: Int) {
        if (result.executionId.isNullOrBlank()) {
            log.warn { "EngineResult has no executionId, skipping snapshot save" }
            return
        }
        if (repository.findByExecutionId(result.executionId!!) != null) return

        val finalState = result.finalState
        val snapshot = WfExecutionSnapshot().apply {
            this.executionId = result.executionId!!
            this.workflowId = workflowId
            this.workflowVersion = version
            this.success = result.success
            this.inputsJson = finalState?.inputs?.let { JsonUtil.serialize(it) }
            this.nodeOutputsJson = finalState?.nodeOutputs?.let { JsonUtil.serialize(it) }
            this.traceJson = result.trace.takeIf { it.isNotEmpty() }?.let { JsonUtil.serialize(it) }
            this.errorMsg = result.errorMsg?.take(512)
        }
        repository.save(snapshot)
        log.debug { "Saved execution snapshot executionId=${result.executionId} workflowId=$workflowId" }
    }

    // ===== Query helpers (admin UI) =====

    /** Paginated execution list filtered by workflow id. */
    fun findSnapshots(workflowId: String, pageable: Pageable): Page<WfExecutionSnapshot> =
        repository.findByWorkflowId(workflowId, pageable)

    /** Raw row lookup (no EngineResult reconstruction) used by debug JSON APIs. */
    fun findByExecutionIdRaw(executionId: String): WfExecutionSnapshot? = repository.findByExecutionId(executionId)

    /** Global paginated list (admin audit view). */
    fun findAll(pageable: Pageable): Page<WfExecutionSnapshot> = repository.findAll(pageable)

    // ===== DebugService.ExecutionLogRepository SPI =====

    /**
     * Reconstruct the full [EngineResult] for a past execution by id.
     *
     * Rebuild pipeline (keeps persisted payload small vs storing the entire
     * ImmutableExecutionState blob):
     *   1. Load WfExecutionSnapshot → deserialize inputs / nodeOutputs maps
     *   2. Load WorkflowDefinition via WfDefinitionService (required metadata to
     *      construct the starting ImmutableExecutionState)
     *   3. ImmutableExecutionState.start(def, inputs) → mergeNodeOutputs(nodeOutputs)
     */
    override fun findByExecutionId(executionId: String): EngineResult? {
        val snap = repository.findByExecutionId(executionId) ?: return null
        val def = definitionService.get(snap.workflowId) ?: return null

        val inputs = snap.inputsJson
            ?.let { JsonUtil.toMap(it) }
            ?: emptyMap()

        val nodeOutputs = snap.nodeOutputsJson
            ?.let { JsonUtil.toMap(it) }
            ?: emptyMap()

        // Rehydrate the (immutable) runtime state as it looked after the final node
        var state = ImmutableExecutionState.start(def, inputs)
        if (nodeOutputs.isNotEmpty()) {
            state = state.mergeNodeOutputs(nodeOutputs)
        }

        return EngineResult.builder()
            .success(snap.success)
            .executionId(snap.executionId)
            .errorMsg(snap.errorMsg)
            .finalState(state)
            .build()
    }

    /** Narrow helper: resolve the workflow id of an execution by execution id. */
    override fun findWorkflowIdByExecutionId(executionId: String): String? =
        repository.findByExecutionId(executionId)?.workflowId

    // ===== DebugService.DefinitionLoader SPI =====

    /**
     * Delegate to [WfDefinitionService.get]. Throws IllegalArgumentException if
     * the definition is missing or inactive (debug trace UI cannot render).
     */
    override fun load(workflowId: String): WorkflowDefinition =
        definitionService.get(workflowId)
            ?: throw IllegalArgumentException("Workflow definition not found: $workflowId")
}
