package com.fluxion.builtin.http

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientRequest
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionResult
import org.slf4j.*

/**
 * 内置 HTTP 调用函数（builtin:httpCall）
 */
class HttpCallFunction(
    private val httpClient: HttpClientAdapter
) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

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
                meta().name,
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

    override fun meta() = BuiltinFunctionMetas.HTTP_CALL

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
     * GET/DELETE 请求将输入拼接到 URL 查询参数；其他方法保持原 URL。
     *
     * 使 [HttpCallFunction] 可统一处理 GET 请求。
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
