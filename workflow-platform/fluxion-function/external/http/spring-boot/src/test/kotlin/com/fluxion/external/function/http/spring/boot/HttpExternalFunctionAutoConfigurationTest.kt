package com.fluxion.external.function.http.spring.boot

import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.external.function.http.HttpExternalFunctionTransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * [HttpExternalFunctionAutoConfiguration] 鑷姩瑁呴厤娴嬭瘯銆?
 */
class HttpExternalFunctionAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HttpExternalFunctionAutoConfiguration::class.java))

    @Test
    fun `auto-config registers HttpExternalFunctionTransport when HttpClientAdapter exists`() {
        contextRunner
            .withUserConfiguration(MockHttpClientAdapterConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(HttpExternalFunctionTransport::class.java)
                val transport = context.getBean(ExternalFunctionTransport::class.java)
                assertThat(transport.protocol()).isEqualTo("http")
            }
    }

    @Test
    fun `auto-config does not register transport when HttpClientAdapter missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(HttpExternalFunctionTransport::class.java)
        }
    }

    @Configuration
    class MockHttpClientAdapterConfig {
        @Bean
        fun httpClientAdapter(): HttpClientAdapter = HttpClientAdapter {
            HttpClientResponse(200, "ok", emptyMap())
        }
    }
}
