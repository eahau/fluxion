package com.fluxion.builtin.http

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientRequest
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
import org.slf4j.MDC

/**
 * Built-in HTTP call function (`builtin:httpCall`).
 *
 * Bridges workflow nodes to external HTTP services through the pluggable
 * [HttpClientAdapter] SPI. The default adapter (OkHttp) is registered by
 * `BuiltinFunctionAutoConfiguration` when OkHttp is on the classpath;
 * deployments can supply a custom adapter bean (WebClient, RestClient, etc.)
 * to override it.
 *
 * Node params:
 * - `url`           — target URL (required). For GET/DELETE the directInput
 *                      Map is appended as query parameters automatically.
 * - `method`        — HTTP verb, default `"GET"`.
 * - `headers`       — optional Map of request headers. `X-Trace-Id` is
 *                      injected automatically from the SLF4J MDC context.
 * - `bodyTemplate`  — raw body string override (rare). When absent the
 *                      `directInput` is JSON-serialized for POST/PUT/PATCH.
 * - `responseType`  — `"json"` (default, parsed) or `"text"` (raw string).
 * - `timeout`       — per-request connect+read timeout in ms, default 5000.
 *
 * Non-2xx responses fail the node with [WorkflowNodeException] so downstream
 * error-handling branches (or the engine's retry mechanism) can take over.
 */
class HttpCallFunction(
    private val httpClient: HttpClientAdapter
) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:httpCall"

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val rawUrl = input.requireParam<String>("url")
        val method = input.param("method", "GET").uppercase()
        val responseType = input.param("responseType", "json")
        val timeout = input.paramAsInt("timeout", 5000)

        val headers = buildHeaders(input)
        val body = buildBody(method, input)
        val url = buildUrl(method, rawUrl, input.directInput)

        val request = HttpClientRequest(url, method, headers, body, timeout)
        log.info { "HttpCallFunction [$method] $url" }

        val response = httpClient.execute(request)

        if (!response.isSuccessful) {
            throw WorkflowNodeException(
                functionName,
                RuntimeException("HTTP ${response.statusCode} from $url: ${response.body}")
            )
        }

        val responseBody = response.body
        if (responseBody.isNullOrBlank()) {
            return FunctionResult.success(null)
        }

        return try {
            val result = if (responseType == "text") responseBody
            else responseBody?.let { JsonUtil.deserialize(it, Any::class.java) }
            FunctionResult.success(result)
        } catch (ex: Exception) {
            log.warn { "HttpCallFunction: response is not valid JSON from $url, returning raw body" }
            FunctionResult.success(responseBody)
        }
    }

    private fun buildHeaders(input: NodeInput): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        (input.param<Any>("headers") as? Map<*, *>)?.forEach { (k, v) ->
            headers[k.toString()] = v.toString()
        }
        MDC.get("traceId")?.let { headers["X-Trace-Id"] = it }
        return headers.ifEmpty { null }
    }

    private fun buildBody(method: String, input: NodeInput): String? {
        if (method == "GET" || method == "DELETE") return null
        input.param<Any>("bodyTemplate")?.let { return it.toString() }
        return input.directInput?.let {
            try { JsonUtil.serialize(it) } catch (_: Exception) { "{}" }
        } ?: "{}"
    }

    /**
     * For GET/DELETE the directInput map is appended as URL query parameters;
     * other methods pass the raw URL through unchanged. This mirrors typical
     * REST conventions — GET carries state in the query string, everything
     * else in the body.
     */
    private fun buildUrl(method: String, url: String, directInput: Any?): String {
        if (method != "GET" && method != "DELETE") return url
        val map = directInput as? Map<*, *> ?: return url
        if (map.isEmpty()) return url
        val separator = if ("?" in url) "&" else "?"
        val params = map.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$url$separator$params"
    }
}
