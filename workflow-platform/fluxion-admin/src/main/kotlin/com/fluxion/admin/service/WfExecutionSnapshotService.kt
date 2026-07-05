package com.fluxion.admin.service

import com.fluxion.admin.entity.WfExecutionSnapshot
import com.fluxion.admin.repository.WfExecutionSnapshotRepository
import com.fluxion.core.debug.DebugService
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.EngineResult
import com.fluxion.runtime.core.spi.ExecutionSnapshotStore
import org.slf4j.LoggerFactory
import org.slf4j.debug
import org.slf4j.warn
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 工作流执行快照服务
 *
 * 职责：
 *   1. 每次工作流执行后持久化快照（executionId + inputs + nodeOutputs）
 *   2. 实现 DebugService.ExecutionLogRepository SPI（按 executionId 重建 EngineResult）
 *   3. 实现 DebugService.DefinitionLoader SPI（按 workflowId 加载定义）
 *
 * 重建逻辑（无需存储完整 ImmutableExecutionState）：
 *   1. 加载 WfExecutionSnapshot（inputs + nodeOutputs）
 *   2. 加载 WorkflowDefinition（WfDefinitionService）
 *   3. ImmutableExecutionState.start(def, inputs).mergeNodeOutputs(nodeOutputs)
 */
@Service
class WfExecutionSnapshotService(
    private val repository: WfExecutionSnapshotRepository,
    private val definitionService: WfDefinitionService
) : DebugService.ExecutionLogRepository, DebugService.DefinitionLoader, ExecutionSnapshotStore {

    private val log = LoggerFactory.getLogger(javaClass)

    // ─── 持久化 ──────────────────────────────────────────────────

    override fun save(result: EngineResult, workflowId: String, version: Int) {
        if (result.executionId.isNullOrBlank()) {
            log.warn { "EngineResult has no executionId, skipping snapshot save" }
            return
        }
        // 幂等：相同 executionId 已存在则跳过（concurrent retry 场景）
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

    // ─── 查询 ────────────────────────────────────────────────────

    fun findSnapshots(workflowId: String, pageable: Pageable): Page<WfExecutionSnapshot> =
        repository.findByWorkflowId(workflowId, pageable)

    fun findByExecutionIdRaw(executionId: String): WfExecutionSnapshot? = repository.findByExecutionId(executionId)

    fun findAll(pageable: Pageable): Page<WfExecutionSnapshot> = repository.findAll(pageable)

    // ─── DebugService.ExecutionLogRepository SPI ─────────────────

    override fun findByExecutionId(executionId: String): EngineResult? {
        val snap = repository.findByExecutionId(executionId) ?: return null
        val def = definitionService.get(snap.workflowId) ?: return null

        val inputs = snap.inputsJson
            ?.let { JsonUtil.toMap(it) }
            ?: emptyMap()

        val nodeOutputs = snap.nodeOutputsJson
            ?.let { JsonUtil.toMap(it) }
            ?: emptyMap()

        // 重建不可变执行状态
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

    override fun findWorkflowIdByExecutionId(executionId: String): String? =
        repository.findByExecutionId(executionId)?.workflowId

    // ─── DebugService.DefinitionLoader SPI ───────────────────────

    override fun load(workflowId: String): WorkflowDefinition =
        definitionService.get(workflowId)
            ?: throw IllegalArgumentException("工作流定义不存在: $workflowId")
}
