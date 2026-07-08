/**
 * Spring Boot auto-configuration for the HTTP external-function transport.
 *
 * Unlike the Dubbo/gRPC siblings, this configuration depends on an
 * operator-supplied [HttpClientAdapter] bean (usually produced by the
 * builtin-functions starter which wires OkHttp or another provider). The
 * `@ConditionalOnBean` / `@ConditionalOnMissingBean` guards make this a
 * safe no-op when no HTTP client has been configured yet.
 */
package com.fluxion.external.function.http.spring.boot

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.external.function.http.HttpExternalFunctionTransport
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Registers [HttpExternalFunctionTransport] whenever an
 * [HttpClientAdapter] bean is available.
 */
@AutoConfiguration
@ConditionalOnClass(HttpClientAdapter::class)
class HttpExternalFunctionAutoConfiguration {

    @Bean
    @ConditionalOnBean(HttpClientAdapter::class)
    @ConditionalOnMissingBean(HttpExternalFunctionTransport::class)
    fun httpExternalFunctionTransport(
        httpClientAdapter: HttpClientAdapter
    ): HttpExternalFunctionTransport = HttpExternalFunctionTransport(httpClientAdapter)
}
