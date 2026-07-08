package com.fluxion.builtin.response

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult

/**
 * Built-in success response wrapper (`builtin:responseWrapper`).
 *
 * Produces the standard `{code, message, data}` envelope used by most
 * REST-facing workflows. `data` is taken from the explicit `data` node
 * parameter if set, otherwise falls back to `directInput` (the upstream
 * node's output) so this function can be dropped as the terminal node
 * in a success path without extra configuration.
 *
 * Node params:
 * - `code`    — numeric status code, default `200`
 * - `message` — human-readable status message, default `"success"`
 * - `data`    — payload override; when absent `directInput` is used
 */
class ResponseWrapperFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override val functionName: String = "builtin:responseWrapper"

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val code = input.paramAsInt("code", 200)
        val message = input.param("message", "success")
        val data = input.param<Any>("data") ?: input.directInput ?: emptyMap<String, Any>()

        val response = mapOf(
            "code" to code,
            "message" to message,
            "data" to data
        )
        return FunctionResult.success(response)
    }
}

/**
 * Built-in error response wrapper (`builtin:errorWrapper`).
 *
 * Same envelope shape as [ResponseWrapperFunction] but tuned for failure
 * paths: the default `code` is `500` and `message` is extracted from a
 * variety of upstream error shapes — a thrown [Throwable], a Map with a
 * `message` key, or a raw string — before falling back to the explicit
 * `message` node parameter. This lets the function be dropped after a
 * catch-branch or an error-handling sub-DAG and "just work".
 *
 * Node params:
 * - `code`    — numeric error code, default `500`
 * - `message` — explicit error message (highest precedence)
 * - `data`    — error detail payload; falls back to `directInput`
 */
class ErrorWrapperFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    override val functionName: String = "builtin:errorWrapper"

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val code = input.paramAsInt("code", 500)
        val message = input.param<String>("message") ?: extractErrorMessage(input.directInput)
        val data = input.param<Any>("data") ?: input.directInput ?: emptyMap<String, Any>()

        val response = mapOf<String, Any?>(
            "code" to code,
            "message" to message,
            "data" to data
        )
        return FunctionResult.success(response)
    }

    private fun extractErrorMessage(input: Any?): String = when {
        input == null -> "Unknown error"
        input is Throwable -> input.message ?: input.javaClass.simpleName
        input is Map<*, *> && input.containsKey("message") -> input["message"].toString()
        else -> input.toString()
    }
}
