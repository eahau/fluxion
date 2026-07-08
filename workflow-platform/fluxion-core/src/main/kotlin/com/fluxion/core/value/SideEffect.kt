package com.fluxion.core.value

/**
 * A side effect produced during function execution that must be reversed if
 * the enclosing Saga aborts.
 *
 * Functions declare side effects by returning them inside [FunctionResult].
 * The Saga executor collects side effects in order and, on failure, walks
 * them in reverse invoking the referenced [compensateFunctionRef] with the
 * stored payload.
 */
data class SideEffect(
    /** Effect category, e.g. `DB_UPDATE`, `MQ_SEND`, `RPC_CALL`. */
    val type: String,
    /** Business target (DB table, MQ topic, RPC service method, ...). */
    val target: String,
    /** Opaque payload passed verbatim to the compensator; typically JSON-serialised. */
    val payload: Any?,
    /** Function ref invoked during compensation; null when reversal is not supported. */
    val compensateFunctionRef: String?
)
