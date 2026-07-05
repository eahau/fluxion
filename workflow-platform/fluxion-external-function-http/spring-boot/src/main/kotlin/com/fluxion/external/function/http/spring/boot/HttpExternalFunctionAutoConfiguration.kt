package com.fluxion.external.function.http.spring.boot

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.external.function.http.HttpExternalFunctionTransport
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * HTTP 外部函数 Transport 自动装配。
 *
 * 依赖 [HttpClientAdapter] Bean（通常由 fluxion-builtin-functions/spring-boot 提供），
 * 条件满足时注册 [HttpExternalFunctionTransport]，协议名为 `http`。
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
