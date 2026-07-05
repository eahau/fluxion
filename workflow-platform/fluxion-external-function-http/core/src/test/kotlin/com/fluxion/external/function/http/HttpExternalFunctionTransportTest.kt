package com.fluxion.external.function.http

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientRequest
import com.fluxion.builtin.http.spi.HttpClientResponse
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [HttpExternalFunctionTransport] 单元测试。
 *
 * 使用内存 [HttpClientAdapter] 模拟下游服务，验证配置到请求体/查询参数的映射及 GET/POST 统一由 [HttpCallFunction] 处理。
 */
class HttpExternalFunctionTransportTest {

    private val transport = HttpExternalFunctionTransport(RecordingHttpClientAdapter())

    @Test
    fun `protocol returns http`() {
        assertThat(transport.protocol()).isEqualTo("http")
    }

    @Test
    fun `missing service returns config error`() {
        val request = request(config = ExternalFunctionConfig(protocol = "http", service = null))

        val response = transport.invoke(request)

        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("HTTP_CONFIG_ERROR")
        assertThat(response.errorMessage).contains("service (url) is required")
    }

    @Test
    fun `GET request delegates to HttpCallFunction with query params`() {
        val adapter = RecordingHttpClientAdapter()
        val httpTransport = HttpExternalFunctionTransport(adapter)
        val config = ExternalFunctionConfig(
            protocol = "http",
            service = "https://api.example.com/users",
            method = "GET",
            headers = mapOf("X-Api-Key" to "secret"),
            endpoint = "should-be-ignored",
            timeoutMs = 3000
        )

        val response = httpTransport.invoke(request(config, input = mapOf("page" to 1)))

        assertThat(response.success).isTrue()
        @Suppress("UNCHECKED_CAST")
        val output = response.output as Map<String, Any>
        assertThat(output["ok"]).isEqualTo(true)
        assertThat(adapter.lastRequest).isNotNull
        val req = adapter.lastRequest!!
        assertThat(req.method).isEqualTo("GET")
        assertThat(req.url).startsWith("https://api.example.com/users")
        assertThat(req.url).contains("page=1")
        assertThat(req.headers).containsEntry("X-Api-Key", "secret")
        assertThat(req.timeoutMs).isEqualTo(3000)
        assertThat(req.body).isNull()
    }

    @Test
    fun `POST request delegates to HttpCallFunction with body`() {
        val adapter = RecordingHttpClientAdapter()
        val httpTransport = HttpExternalFunctionTransport(adapter)
        val config = ExternalFunctionConfig(
            protocol = "http",
            service = "https://api.example.com/users",
            method = "POST",
            headers = mapOf("Content-Type" to "application/json"),
            timeoutMs = 5000,
            extras = mapOf("responseType" to "json")
        )

        val response = httpTransport.invoke(request(config, input = mapOf("name" to "Fluxion")))

        assertThat(response.success).isTrue()
        assertThat(response.output).isEqualTo(mapOf("id" to 1, "name" to "Fluxion"))
        val req = adapter.lastRequest!!
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url).isEqualTo("https://api.example.com/users")
        assertThat(req.body).isEqualTo("""{"name":"Fluxion"}""")
    }

    @Test
    fun `non-2xx response returns error`() {
        val adapter = RecordingHttpClientAdapter { HttpClientResponse(500, "Internal Server Error", emptyMap()) }
        val httpTransport = HttpExternalFunctionTransport(adapter)
        val config = ExternalFunctionConfig(
            protocol = "http",
            service = "https://api.example.com/fail",
            method = "GET"
        )

        val response = httpTransport.invoke(request(config))

        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("HTTP_INVOKE_ERROR")
    }

    private fun request(
        config: ExternalFunctionConfig,
        input: Any? = null
    ): ExternalFunctionRequest = ExternalFunctionRequest(
        config = config,
        input = input,
        functionRef = "test:http"
    )

    private class RecordingHttpClientAdapter(
        private val responseProvider: (HttpClientRequest) -> HttpClientResponse = { defaultResponse(it) }
    ) : HttpClientAdapter {

        var lastRequest: HttpClientRequest? = null

        override fun execute(request: HttpClientRequest): HttpClientResponse {
            lastRequest = request
            return responseProvider(request)
        }

        companion object {
            private fun defaultResponse(request: HttpClientRequest): HttpClientResponse {
                val body = if (request.method == "GET") """{"ok":true}""" else """{"id":1,"name":"Fluxion"}"""
                return HttpClientResponse(200, body, mapOf("content-type" to "application/json"))
            }
        }
    }
}
