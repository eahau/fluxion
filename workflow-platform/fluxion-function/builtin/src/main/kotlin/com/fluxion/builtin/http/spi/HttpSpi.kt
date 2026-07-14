package com.fluxion.builtin.http.spi

/**
 * Immutable HTTP request value-object — shared between the [HttpClientAdapter]
 * SPI and its callers (e.g. [com.fluxion.builtin.http.HttpCallFunction]).
 *
 * @property url full target URL (scheme + host + path + static query)
 * @property method HTTP verb: GET / POST / PUT / DELETE / PATCH (uppercase)
 * @property headers optional request headers
 * @property body serialized request body, `null` for GET/DELETE
 * @property timeoutMs per-request read+connect timeout in milliseconds
 */
data class HttpClientRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>?,
    val body: String?,
    val timeoutMs: Int
) {
    companion object {
        /** Convenience factory for GET requests. */
        @JvmStatic
        fun get(url: String, headers: Map<String, String>?, timeoutMs: Int): HttpClientRequest =
            HttpClientRequest(url, "GET", headers, null, timeoutMs)

        /** Convenience factory for POST requests. */
        @JvmStatic
        fun post(url: String, headers: Map<String, String>?, body: String?, timeoutMs: Int): HttpClientRequest =
            HttpClientRequest(url, "POST", headers, body, timeoutMs)
    }
}

/**
 * Immutable HTTP response value-object returned by [HttpClientAdapter.execute].
 *
 * @property statusCode HTTP status (e.g. 200, 404, 500)
 * @property body response body as a UTF-8 string; null on empty response
 * @property headers response headers (case-insensitive access depends on the
 *   concrete adapter implementation; callers should iterate key/value pairs)
 */
data class HttpClientResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String>
) {
    /** True for the standard 2xx success range. */
    val isSuccessful: Boolean get() = statusCode in 200..299
}

/**
 * HTTP client SPI used by the built-in `httpCall` function.
 *
 * Kept intentionally tiny so it can be implemented by any client — the
 * default adapter ships in [com.fluxion.builtin.http.okhttp.OkHttpClientAdapter]
 * but deployments can replace it with a custom one (e.g. WebClient, RestClient)
 * by registering their own [HttpClientAdapter] Spring bean.
 */
fun interface HttpClientAdapter {
    /** Executes the request and returns the parsed response (blocking). */
    fun execute(request: HttpClientRequest): HttpClientResponse
}
