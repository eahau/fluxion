package com.fluxion.core.value

import com.fluxion.core.enums.FunctionStatus

/**
 * Typed return value from a single function invocation.
 *
 * Functions may return a raw value (which the engine wraps in
 * [FunctionResult.success] automatically) or a `FunctionResult` directly
 * when they need to report [FunctionStatus.SKIPPED] or attach side-effects
 * for Saga compensation.
 *
 * @param O the declared output type of the function.
 */
data class FunctionResult<O>(
    /** Value produced by the function; null when status != SUCCESS. */
    val output: O?,
    /** Terminal status of this invocation. */
    val status: FunctionStatus,
    /** Side effects produced; consumed by [SagaExecutor] during compensation planning. */
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
