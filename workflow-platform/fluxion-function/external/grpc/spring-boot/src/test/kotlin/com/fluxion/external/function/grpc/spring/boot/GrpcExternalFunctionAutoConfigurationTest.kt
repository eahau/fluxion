package com.fluxion.external.function.grpc.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * [GrpcExternalFunctionAutoConfiguration] 鑷姩瑁呴厤娴嬭瘯銆?
 */
class GrpcExternalFunctionAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(GrpcExternalFunctionAutoConfiguration::class.java))

    @Test
    fun `auto-config registers GrpcExternalFunctionTransport bean`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(ExternalFunctionTransport::class.java)
            val transport = context.getBean(ExternalFunctionTransport::class.java)
            assertThat(transport.protocol()).isEqualTo("grpc")
        }
    }
}
