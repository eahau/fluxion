package com.fluxion.core.value

import com.fluxion.core.model.ImmutableExecutionState

/**
 * 工作流引擎执行结果。
 *
 * 包含执行成功/失败状态、最终输出数据、执行轨迹、不可变状态快照。
 * 由 [WorkflowEngine] / [DagExecutor] 产出，经 [UnifiedResponse] 转换后返回给适配器层。
 */
data class EngineResult(
    /** 是否执行成功 */
    val success: Boolean,
    /** 最终输出数据（工作流末尾节点输出） */
    val data: Any? = null,
    /** 执行唯一 ID（UUID，用于链路追踪和快照关联） */
    val executionId: String? = null,
    /** 错误信息（失败时填充） */
    val errorMsg: String? = null,
    /** 执行轨迹（按完成顺序记录的节点执行记录） */
    val trace: MutableList<NodeExecutionRecord> = mutableListOf(),
    /** 最终不可变状态快照（供调试/重放使用） */
    val finalState: ImmutableExecutionState? = null
) {
    companion object {
        /** 创建成功结果（从状态快照提取 executionId） */
        @JvmStatic
        fun success(data: Any?, state: ImmutableExecutionState) = EngineResult(
            success = true,
            data = data,
            executionId = state.meta.executionId,
            finalState = state
        )

        /** 创建失败结果 */
        @JvmStatic
        fun failure(errorMsg: String?, executionId: String?) = EngineResult(
            success = false,
            errorMsg = errorMsg,
            executionId = executionId
        )

        /** 创建 Builder（流式构建） */
        @JvmStatic
        fun builder() = Builder()
    }

    /** 流式构建器 */
    class Builder {
        var success: Boolean = false
        var data: Any? = null
        var executionId: String? = null
        var errorMsg: String? = null
        var trace: MutableList<NodeExecutionRecord> = mutableListOf()
        var finalState: ImmutableExecutionState? = null

        fun success(v: Boolean) = apply { success = v }
        fun data(v: Any?) = apply { data = v }
        fun executionId(v: String?) = apply { executionId = v }
        fun errorMsg(v: String?) = apply { errorMsg = v }
        fun trace(v: MutableList<NodeExecutionRecord>) = apply { trace = v }
        fun finalState(v: ImmutableExecutionState?) = apply { finalState = v }

        fun build() = EngineResult(
            success = success,
            data = data,
            executionId = executionId,
            errorMsg = errorMsg,
            trace = trace,
            finalState = finalState
        )
    }
}
