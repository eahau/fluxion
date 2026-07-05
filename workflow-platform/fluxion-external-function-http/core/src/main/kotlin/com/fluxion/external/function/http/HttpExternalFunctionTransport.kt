package com.fluxion.external.function.http

import com.fluxion.builtin.http.HttpCallFunction
import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.core.enums.FunctionStatus
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.model.NodeInput
import org.slf4j.*

/**
 * HTTP 外部函数 Transport：复用 [HttpCallFunction] 完成 Outbound HTTP 调用。
 *
 * 远程 HTTP 客户端（[HttpClientAdapter]）由使用方提供并在服务启动时注入，
 * transport 本身不创建默认 HTTP 客户端，避免在运行时为使用方构建远程端点。
 *
 * 配置映射：
 * - `service` → URL
 * - `method`  → HTTP 方法（GET/POST/PUT/DELETE/PATCH，默认 GET）
 * - `headers` → 请求头
 * - `timeoutMs` → 超时（毫秒）
 * - `extras.bodyTemplate` → 显式请求体模板；为空时序列化 `input` 作为请求体
 * - `extras.responseType` → `json`（默认）或 `text`
 *
 * 所有 HTTP 方法统一由 [HttpCallFunction] 处理。
 */
class HttpExternalFunctionTransport(
    httpClient: HttpClientAdapter
) : ExternalFunctionTransport {

    private val log = LoggerFactory.getLogger(javaClass)

    private val httpCallFunction = HttpCallFunction(httpClient)

    override fun protocol(): String = "http"

    override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse {
        val config = request.config
        val url = config.service
            ?: return errorResponse("service (url) is required for http protocol")
        val method = (config.method ?: "GET").uppercase()

        val input = buildNodeInput(config, request.input)

        return try {
            log.debug { "HTTP external invoke [$method] $url" }
            val result = httpCallFunction.apply(input)
            when (result.status) {
                FunctionStatus.SUCCESS -> ExternalFunctionResponse(output = result.output, success = true)
                else -> ExternalFunctionResponse(
                    output = result.output,
                    success = false,
                    errorCode = "HTTP_INVOKE_FAILED",
                    errorMessage = "HTTP invoke returned status ${result.status}"
                )
            }
        } catch (ex: Exception) {
            log.error(ex) { "HTTP external invoke failed: [$method] $url" }
            ExternalFunctionResponse(
                output = null,
                success = false,
                errorCode = "HTTP_INVOKE_ERROR",
                errorMessage = ex.message ?: ex.javaClass.name
            )
        }
    }

    private fun buildNodeInput(config: ExternalFunctionConfig, input: Any?): NodeInput {
        val params = mutableMapOf<String, Any>(
            "url" to config.service!!,
            "method" to (config.method ?: "GET").uppercase()
        )
        config.headers?.let { params["headers"] = it }
        config.timeoutMs.takeIf { it > 0 }?.let { params["timeout"] = it.toInt() }
        config.extras?.get("responseType")?.let { params["responseType"] = it }
        config.extras?.get("bodyTemplate")?.let { params["bodyTemplate"] = it }

        return NodeInput(directInput = input, nodeParams = params)
    }

    private fun errorResponse(message: String): ExternalFunctionResponse = ExternalFunctionResponse(
        output = null,
        success = false,
        errorCode = "HTTP_CONFIG_ERROR",
        errorMessage = message
    )
}
