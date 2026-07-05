package com.fluxion.builtin.http.okhttp

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientRequest
import com.fluxion.builtin.http.spi.HttpClientResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.*
import java.util.concurrent.TimeUnit

/**
 * OkHttp 实现的 HttpClientAdapter
 */
class OkHttpClientAdapter(private val client: OkHttpClient) : HttpClientAdapter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun execute(request: HttpClientRequest): HttpClientResponse {
        val callClient = if (request.timeoutMs > 0) {
            client.newBuilder()
                .callTimeout(request.timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .build()
        } else client

        val builder = Request.Builder().url(request.url)

        // 请求头
        request.headers?.forEach { (k, v) -> builder.addHeader(k, v) }

        // 请求体
        val method = request.method.uppercase()
        val body = if (method != "GET" && method != "DELETE") {
            (request.body ?: "").toRequestBody(jsonMediaType)
        } else null
        builder.method(method, body)

        log.debug { "OkHttpClientAdapter [$method] ${request.url}" }

        return callClient.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val headers = mutableMapOf<String, String>()
            response.headers.forEach { (name, value) -> headers[name] = value }
            HttpClientResponse(response.code, responseBody, headers)
        }
    }
}
