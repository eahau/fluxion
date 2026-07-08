/**
 * HTTP-backed external function transport.
 *
 * Delegates the actual outbound HTTP call to the shared
 * [com.fluxion.builtin.http.HttpCallFunction] helper, which in turn uses an
 * injectable [com.fluxion.builtin.http.spi.HttpClientAdapter]. Transporting
 * through the same helper as BUILTIN `httpCall` nodes keeps:
 * * Connection pool, TLS and retry configuration consistent.
 * * Observability (tracing, metrics) emitted with the same tag vocabulary.
 * * Body serialisation / response deserialisation logic in one place.
 *
 * Config mapping from the generic surface to `HttpCallFunction` parameters:
 * * `service`     → `url`
 * * `method`      → HTTP verb (GET/POST/PUT/DELETE/PATCH, default GET)
 * * `headers`     → HTTP request headers
 * * `timeoutMs`   → call timeout, converted to seconds for the helper
 * * `extras.bodyTemplate` → explicit body template; if absent, `input` is
 *   serialised as JSON automatically.
 * * `extras.responseType` → `"json"` (default) or `"text"`
 */
package com.fluxion.external.function.http

import com.fluxion.builtin.http.HttpCallFunction
import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.core.enums.FunctionStatus
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.model.NodeInput
import org.slf4j.LoggerFactory

/**
 * Transport implementation that reuses the builtin `HttpCallFunction` and
 * its pluggable `HttpClientAdapter` SPI.
 *
 * @property httpClient adapter supplied by the hosting application (typically
 *   auto-configured by the builtin HTTP starter)
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
