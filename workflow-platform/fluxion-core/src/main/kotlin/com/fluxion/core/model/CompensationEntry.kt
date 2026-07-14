package com.fluxion.core.model

import com.fluxion.core.value.SideEffect

/**
 * A row in the Saga compensation plan.
 *
 * [SagaExecutor] builds an ordered list of CompensationEntry as each
 * node succeeds; on failure it walks the list in *reverse* and invokes
 * [compensateFunctionRef], passing the stored [sideEffects] and the
 * node's original [output] so the compensator can reconstruct the
 * pre-side-effect state.
 */
data class CompensationEntry(
    /** The node that produced the side effect(s). */
    val nodeId: String,
    /** Human-readable node name for audit / error messages. */
    val nodeName: String,
    /** Function ref used during compensation (may be null for no-op nodes). */
    val compensateFunctionRef: String?,
    /** Side effects the compensator must reverse. */
    val sideEffects: List<SideEffect>,
    /** Original node output — supplied as `directInput` to the compensate function. */
    val output: Any?
)
