/**
 * Response envelope returned by every
 * [ExternalFunctionTransport.invoke] call.
 *
 * Transports MUST convert their native error types to a failed response
 * (success=false, errorCode/message populated) rather than throwing. The
 * engine uses the structured envelope to drive error-strategy logic,
 * compensation planners and admin trace views.
 */
package com.fluxion.core.function.external

/**
 * Immutable result envelope describing one external-function invocation.
 *
 * @property output serialised result payload on success; `null` or a
 *   partial value on transport-defined graceful failures
 * @property success `true` when the remote side accepted and returned a
 *   semantically successful answer
 * @property errorCode stable machine-readable error category, typically
 *   `PROTOCOL_ERROR`/`REMOTE_5XX`/`CONFIG_ERROR` style constants
 * @property errorMessage human readable message, usually the remote-side
 *   status text or transport-level exception message
 */
data class ExternalFunctionResponse(
    val output: Any?,
    val success: Boolean = true,
    val errorCode: String? = null,
    val errorMessage: String? = null
)
