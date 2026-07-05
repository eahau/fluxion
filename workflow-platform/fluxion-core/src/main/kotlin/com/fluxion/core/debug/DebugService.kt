package com.fluxion.core.debug

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.core.value.RerunResult
import org.slf4j.LoggerFactory
import java.util.stream.Collectors

/**
 * 调试服务 — 提供重放、断点、单步执行等调试能力
 *
 * 核心优势：利用 ImmutableExecutionState 的不可变特性，
 *          无需重新执行前置节点即可精确恢复到任意节点状态。
 */
class DebugService(
    private val engine: WorkflowEngine,
    private val executionLogRepository: ExecutionLogRepository,
    private val definitionLoader: DefinitionLoader
) {
    private val log = LoggerFactory.getLogger(DebugService::class.java)

    // ─── Rerun ───────────────────────────────────────────────────

    /**
     * 从指定节点开始重放执行。
     *
     * 利用原始执行状态的不可变快照，恢复目标节点之前的所有节点输出，
     * 然后从目标节点开始重新执行后续节点（支持覆盖输入）。
     *
     * @param executionId    原始执行的 ID
     * @param targetNodeId   从哪个节点开始重放
     * @param overrideInput  覆盖输入（null 则使用前驱节点输出）
     * @return 重放结果（含新的最终状态和执行轨迹）
     */
    suspend fun rerunFromNode(executionId: String, targetNodeId: String, overrideInput: Any?): RerunResult {
        val originalResult = executionLogRepository.findByExecutionId(executionId)
            ?: throw IllegalArgumentException("Execution not found: $executionId")

        val originalState = originalResult.finalState!!
        val def = loadDefinition(executionId)

        val targetIndex = findNodeIndex(def, targetNodeId)
        require(targetIndex >= 0) { "Node not found in workflow: $targetNodeId" }

        // 保留目标节点之前的所有节点输出
        val priorOutputs = originalState.nodeOutputs.entries
            .filter { (key, _) -> isBeforeNode(def, key, targetNodeId) }
            .associate { it.key to it.value }

        val replayState = ImmutableExecutionState
            .start(def, originalState.inputs)
            .mergeNodeOutputs(priorOutputs)

        val directInput = overrideInput ?: getPrevNodeOutput(originalState, def, targetNodeId)

        val nodesToRerun = def.nodes.subList(targetIndex, def.nodes.size)

        log.info("Rerunning from node [{}] in execution [{}], {} nodes to execute",
            targetNodeId, executionId, nodesToRerun.size)

        return engine.executePartial(nodesToRerun, directInput, replayState)
    }

    // ─── Step / Continue ─────────────────────────────────────────

    /**
     * 单步执行：执行下一个节点后暂停。
     *
     * @param snap  当前调试快照
     * @return 新的调试快照（nextNodeIndex + 1）
     */
    suspend fun step(snap: DebugSnapshot): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        return doStep(snap, def)
    }

    /**
     * 继续执行直到遇到断点或工作流结束。
     *
     * @param snap  当前调试快照
     * @return 新的调试快照（pausedAtNodeId 不为 null 表示停在断点）
     */
    suspend fun continueToBreakpoint(snap: DebugSnapshot): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        var current = snap
        val nodes = def.nodes

        while (current.nextNodeIndex < nodes.size) {
            current = doStep(current, def)
            if (current.pausedAtNodeId != null) break
        }
        return current
    }

    // ─── Snapshot ────────────────────────────────────────────────

    /**
     * 从执行结果创建初始调试快照。
     *
     * 将 EngineResult.finalState 封装为 DebugSnapshot，
     * 支持设置断点集合和 Mock 配置。
     */
    fun createSnapshot(
        result: EngineResult,
        workflowId: String,
        workflowVersion: Int,
        breakpoints: Set<String>?,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): DebugSnapshot = DebugSnapshot(
        workflowId = workflowId,
        workflowVersion = workflowVersion,
        executionState = result.finalState!!,
        traces = result.trace,
        breakpoints = breakpoints ?: emptySet(),
        mockConfig = mockConfig,
        pausedAtNodeId = null,
        nextNodeIndex = result.trace.size
    )

    // ─── Private Helpers ─────────────────────────────────────────

    /** 执行单步（内部方法）：恢复上下文、执行节点、更新状态 */
    private suspend fun doStep(snap: DebugSnapshot, def: WorkflowDefinition): DebugSnapshot {
        val ctx = DebugSnapshot.restore(snap, def)
        val idx = ctx.nextNodeIndex
        val nodes = def.nodes

        if (idx >= nodes.size) return snap

        val node = nodes[idx]
        val state = ctx.executionState

        val directInput: Any? = if (idx == 0) state.inputs else state.getNodeOutput(nodes[idx - 1].id)

        val trace = ctx.traces.toMutableList()
        val record = engine.executeNode(node, directInput, state, ctx.mockConfig)
        trace.add(record)

        val newState = state.withNodeOutput(node.id, record.output)
        val pausedAt = if (ctx.breakpoints.contains(node.id)) node.id else null

        return DebugContext(newState, ctx.mockConfig, ctx.breakpoints, trace, idx + 1)
            .toSnapshot(snap.workflowId, snap.workflowVersion, pausedAt)
    }

    private fun loadDefinition(executionId: String): WorkflowDefinition {
        val workflowId = executionLogRepository.findWorkflowIdByExecutionId(executionId)
            ?: throw IllegalArgumentException("No workflow bound to execution: $executionId")
        return definitionLoader.load(workflowId)
    }

    private fun findNodeIndex(def: WorkflowDefinition, nodeId: String): Int =
        def.nodes.indexOfFirst { it.id == nodeId }

    private fun isBeforeNode(def: WorkflowDefinition, nodeAId: String, nodeBId: String): Boolean {
        val a = findNodeIndex(def, nodeAId)
        val b = findNodeIndex(def, nodeBId)
        return a >= 0 && b >= 0 && a < b
    }

    private fun getPrevNodeOutput(state: ImmutableExecutionState, def: WorkflowDefinition, targetNodeId: String): Any? {
        val idx = findNodeIndex(def, targetNodeId)
        if (idx <= 0) return state.inputs
        return state.getNodeOutput(def.nodes[idx - 1].id)
    }

    // ─── SPI Interfaces ──────────────────────────────────────────

    /** 执行日志仓储 SPI（由 workflow-admin 实现） */
    interface ExecutionLogRepository {
        fun findByExecutionId(executionId: String): EngineResult?
        fun findWorkflowIdByExecutionId(executionId: String): String?
    }

    /** 工作流定义加载器 SPI（由 workflow-admin 实现） */
    interface DefinitionLoader {
        fun load(workflowId: String): WorkflowDefinition
    }
}
