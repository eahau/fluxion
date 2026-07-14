package com.fluxion.outbound.http.spring.boot
import com.fluxion.builtin.http.spi.HttpClientAdapter
import com.fluxion.builtin.http.spi.HttpClientResponse
import com.fluxion.outbound.OutboundTransport
import com.fluxion.outbound.http.HttpOutboundTransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
 */
class HttpOutboundAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(HttpOutboundAutoConfiguration::class.java))
    @Test
    fun `auto-config registers HttpOutboundTransport when HttpClientAdapter exists`() {
        contextRunner
            .withUserConfiguration(MockHttpClientAdapterConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(HttpOutboundTransport::class.java)
                val transport = context.getBean(OutboundTransport::class.java)
                assertThat(transport.protocol()).isEqualTo("http")
            }
    }
    @Test
    fun `auto-config does not register transport when HttpClientAdapter missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(HttpOutboundTransport::class.java)
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
