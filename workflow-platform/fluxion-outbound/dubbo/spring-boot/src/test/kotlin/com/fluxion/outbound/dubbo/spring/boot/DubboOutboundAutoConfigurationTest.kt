package com.fluxion.outbound.dubbo.spring.boot
import com.fluxion.outbound.OutboundTransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
 */
class DubboOutboundAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DubboOutboundAutoConfiguration::class.java))
    @Test
    fun `auto-config registers DubboOutboundTransport bean`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(OutboundTransport::class.java)
            val transport = context.getBean(OutboundTransport::class.java)
            assertThat(transport.protocol()).isEqualTo("dubbo")
        }
    }
}
