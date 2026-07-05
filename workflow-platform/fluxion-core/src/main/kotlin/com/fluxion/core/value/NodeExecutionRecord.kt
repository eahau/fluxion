package com.fluxion.core.value

import com.fluxion.core.enums.NodeStatus
import com.fluxion.core.model.WorkflowNode

/**
 * 节点执行记录 — 执行轨迹的基本单元（类比调用栈帧快照）
 */
data class NodeExecutionRecord(
    /** 节点 ID */
    val nodeId: String,
    /** 节点名称（日志/调试显示用） */
    val nodeName: String,
    /** 执行状态（SUCCESS / FAILED / SKIPPED / FALLBACK） */
    val status: NodeStatus,
    /** 节点输出（下一节点的 directInput） */
    val output: Any?,
    /** 节点输入（调试/重放用，默认 null） */
    val input: Any?,
    /** 声明的副作用列表（供 Saga 回滚使用） */
    val sideEffects: List<SideEffect>,
    /** 失败时的异常（成功时为 null） */
    val error: Throwable?,
    /** 执行耗时（毫秒） */
    val durationMs: Long,
    /** 重试次数（0 = 首次执行） */
    val retryAttempt: Int
) {
    /** 异常消息快捷访问 */
    val errorMessage: String? get() = error?.message

    companion object {
        /** 创建成功记录 */
        @JvmStatic
        fun success(node: WorkflowNode, output: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SUCCESS,
            output = output, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** 创建成功记录（含输入快照，用于调试回放） */
        @JvmStatic
        fun successWithInput(node: WorkflowNode, output: Any?, input: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SUCCESS,
            output = output, input = input, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** 创建失败记录 */
        @JvmStatic
        fun failed(node: WorkflowNode, error: Throwable?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.FAILED,
            output = null, input = null, sideEffects = emptyList(),
            error = error, durationMs = durationMs, retryAttempt = 0
        )

        /** 创建跳过记录 */
        @JvmStatic
        fun skipped(node: WorkflowNode, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SKIPPED,
            output = null, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** 创建降级成功记录 */
        @JvmStatic
        fun fallback(node: WorkflowNode, output: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.FALLBACK,
            output = output, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )
    }
}
