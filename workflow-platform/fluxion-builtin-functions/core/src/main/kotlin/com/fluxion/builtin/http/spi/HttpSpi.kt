package com.fluxion.builtin.http.spi

/**
 * HTTP 请求值对象（协议无关）
 */
data class HttpClientRequest(
    val url: String,
    val method: String,       // GET / POST / PUT / DELETE / PATCH
    val headers: Map<String, String>?,
    val body: String?,        // JSON 字符串，GET/DELETE 时为 null
    val timeoutMs: Int
) {
    companion object {
        @JvmStatic
        fun get(url: String, headers: Map<String, String>?, timeoutMs: Int): HttpClientRequest =
            HttpClientRequest(url, "GET", headers, null, timeoutMs)

        @JvmStatic
        fun post(url: String, headers: Map<String, String>?, body: String?, timeoutMs: Int): HttpClientRequest =
            HttpClientRequest(url, "POST", headers, body, timeoutMs)
    }
}

/**
 * HTTP 响应值对象（协议无关）
 */
data class HttpClientResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String>
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

/**
 * HTTP 客户端适配器 SPI
 */
fun interface HttpClientAdapter {
    /**
     * 执行 HTTP 请求
     */
    fun execute(request: HttpClientRequest): HttpClientResponse
}
