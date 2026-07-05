package com.fluxion.core.value

import com.fluxion.core.enums.FunctionStatus

/**
 * 函数执行结果 — 显式声明副作用
 *
 * @param O 业务输出类型
 */
data class FunctionResult<O>(
    /** 业务输出（下一节点的 directInput） */
    val output: O?,
    /** 函数执行状态 */
    val status: FunctionStatus,
    /** 声明发生的副作用（DB写、MQ发送），供 Saga 回滚使用 */
    val sideEffects: List<SideEffect> = emptyList()
) {
    companion object {
        @JvmStatic
        fun <O> success(output: O): FunctionResult<O> =
            FunctionResult(output, FunctionStatus.SUCCESS)

        @JvmStatic
        fun <O> successWithEffects(output: O, effects: List<SideEffect>): FunctionResult<O> =
            FunctionResult(output, FunctionStatus.SUCCESS, effects)

        @JvmStatic
        fun <O> skipped(): FunctionResult<O> =
            FunctionResult(null, FunctionStatus.SKIPPED)
    }
}
