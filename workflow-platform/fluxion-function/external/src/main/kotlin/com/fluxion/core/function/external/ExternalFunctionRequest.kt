/**
 * Request envelope passed from the engine into an
 * [ExternalFunctionTransport.invoke] call.
 */
package com.fluxion.core.function.external

/**
 * Immutable invocation request carrying routing config plus the engine
 * supplied input payload.
 *
 * @property config routing + transport options for this specific function
 * @property input serialised node input produced by the DAG executor; may
 *   be a primitive, `Map`, `List` or `null` depending on upstream nodes
 * @property functionRef canonical function reference, used for logging and
 *   error attribution inside the transport
 * @property description optional human-readable note, shown in admin
 *   observability views
 */
data class ExternalFunctionRequest(
    val config: ExternalFunctionConfig,
    val input: Any?,
    val functionRef: String,
    val description: String? = null
)
