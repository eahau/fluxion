package com.fluxion.external.function.dubbo

import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import org.apache.dubbo.config.ApplicationConfig
import org.apache.dubbo.config.ServiceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [DubboExternalFunctionTransport] 本地集成测试。
 *
 * 使用 Dubbo injvm 协议在单 JVM 内暴露并泛化调用示例服务，
 * 不依赖外部注册中心或网络端口。
 */
class DubboExternalFunctionTransportTest {

    private val transport = DubboExternalFunctionTransport()
    private val exportedServices = mutableListOf<ServiceConfig<*>>()

    @BeforeEach
    fun setUp() {
        System.setProperty("dubbo.application.logger", "slf4j")
        System.setProperty("dubbo.application.qos.enable", "false")
        System.setProperty("dubbo.config.mode", "IGNORE")
    }

    @AfterEach
    fun tearDown() {
        exportedServices.forEach { it.unexport() }
        exportedServices.clear()
    }

    @Test
    fun `protocol returns dubbo`() {
        assertThat(transport.protocol()).isEqualTo("dubbo")
    }

    @Test
    fun `missing service returns config error`() {
        val request = request(config = ExternalFunctionConfig(protocol = "dubbo", service = null, method = "sayHello"))

        val response = transport.invoke(request)

        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("DUBBO_CONFIG_ERROR")
        assertThat(response.errorMessage).contains("service is required")
    }

    @Test
    fun `missing method returns config error`() {
        val request = request(config = ExternalFunctionConfig(protocol = "dubbo", service = DemoService::class.java.name, method = null))

        val response = transport.invoke(request)

        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("DUBBO_CONFIG_ERROR")
        assertThat(response.errorMessage).contains("method is required")
    }

    @Test
    fun `generic invoke with single map argument`() {
        exportService(DemoServiceImpl())

        val config = ExternalFunctionConfig(
            protocol = "dubbo",
            service = DemoService::class.java.name,
            method = "greet",
            endpoint = "injvm://127.0.0.1",
            extras = mapOf("parameterTypes" to listOf(DemoRequest::class.java.name))
        )
        val request = request(
            config = config,
            input = mapOf("name" to "Fluxion", "age" to 3)
        )

        val response = transport.invoke(request)

        assertThat(response.success).isTrue()
        @Suppress("UNCHECKED_CAST")
        val output = response.output as Map<String, Any>
        assertThat(output["message"]).isEqualTo("Hello, Fluxion")
    }

    @Test
    fun `generic invoke with multiple arguments`() {
        exportService(DemoServiceImpl())

        val config = ExternalFunctionConfig(
            protocol = "dubbo",
            service = DemoService::class.java.name,
            method = "add",
            endpoint = "injvm://127.0.0.1",
            extras = mapOf(
                "parameterTypes" to listOf("int", "int"),
                "args" to listOf("\$a", "\$b")
            )
        )
        val request = request(
            config = config,
            input = mapOf("a" to 10, "b" to 32)
        )

        val response = transport.invoke(request)

        assertThat(response.success).isTrue()
        assertThat(response.output).isEqualTo(42)
    }

    @Test
    fun `generic invoke returns primitive result`() {
        exportService(DemoServiceImpl())

        val config = ExternalFunctionConfig(
            protocol = "dubbo",
            service = DemoService::class.java.name,
            method = "ping",
            endpoint = "injvm://127.0.0.1",
            extras = mapOf("parameterTypes" to emptyList<String>())
        )
        val request = request(config = config)

        val response = transport.invoke(request)

        assertThat(response.success).isTrue()
        assertThat(response.output).isEqualTo("pong")
    }

    @Test
    fun `shutdown releases references and rejects further invoke`() {
        exportService(DemoServiceImpl())
        val config = ExternalFunctionConfig(
            protocol = "dubbo",
            service = DemoService::class.java.name,
            method = "ping",
            endpoint = "injvm://127.0.0.1",
            extras = mapOf("parameterTypes" to emptyList<String>())
        )
        transport.invoke(request(config = config))

        transport.shutdown()

        val response = transport.invoke(request(config = config))
        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("DUBBO_CONFIG_ERROR")
        assertThat(response.errorMessage).isEqualTo("Dubbo transport is shutdown")
    }

    @Test
    fun `shutdown is idempotent`() {
        transport.shutdown()
        transport.shutdown()
        // should not throw
    }

    @Suppress("DEPRECATION")
    private fun exportService(impl: DemoService) {
        val serviceConfig = ServiceConfig<DemoService>()
        serviceConfig.setInterface(DemoService::class.java)
        serviceConfig.ref = impl
        serviceConfig.application = ApplicationConfig("fluxion-external-function-test-provider")
        serviceConfig.export()
        exportedServices.add(serviceConfig)
    }

    private fun request(
        config: ExternalFunctionConfig,
        input: Any? = null
    ): ExternalFunctionRequest = ExternalFunctionRequest(
        config = config,
        input = input,
        functionRef = "test:dubbo"
    )

    interface DemoService {
        fun greet(request: DemoRequest): DemoResponse
        fun add(a: Int, b: Int): Int
        fun ping(): String
    }

    class DemoServiceImpl : DemoService {
        override fun greet(request: DemoRequest): DemoResponse {
            return DemoResponse("Hello, ${request.name}")
        }

        override fun add(a: Int, b: Int): Int = a + b

        override fun ping(): String = "pong"
    }

    data class DemoRequest(val name: String = "", val age: Int = 0)
    data class DemoResponse(val message: String = "")
}
