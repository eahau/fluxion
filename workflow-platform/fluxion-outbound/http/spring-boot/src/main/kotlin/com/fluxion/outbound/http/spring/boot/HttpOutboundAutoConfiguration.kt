/**
 * Spring Boot auto-configuration for the HTTP external-function transport.
 *
 * Unlike the Dubbo/gRPC siblings, this configuration depends on an
 * operator-supplied [HttpClientAdapter] bean (usually produced by the
 * builtin-functions starter which wires OkHttp or another provider). The
 * `@ConditionalOnBean` / `@ConditionalOnMissingBean` guards make this a
 * safe no-op when no HTTP client has been configured yet.
 */
package com.fluxion.outbound.http.spring.boot

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.outbound.http.HttpOutboundTransport
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Registers [HttpOutboundTransport] whenever an
 * [HttpClientAdapter] bean is available.
 */
@AutoConfiguration
@ConditionalOnClass(HttpClientAdapter::class)
class HttpOutboundAutoConfiguration {

    @Bean
    @ConditionalOnBean(HttpClientAdapter::class)
    @ConditionalOnMissingBean(HttpOutboundTransport::class)
    fun httpOutboundTransport(
        httpClientAdapter: HttpClientAdapter
    ): HttpOutboundTransport = HttpOutboundTransport(httpClientAdapter)
}
