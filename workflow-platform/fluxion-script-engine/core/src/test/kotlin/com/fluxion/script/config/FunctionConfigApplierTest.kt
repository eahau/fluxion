package com.fluxion.script.config

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FunctionConfigApplierTest {

    private class CapturingTransport(val protocolName: String) : ExternalFunctionTransport {
        val preparedConfigs = mutableListOf<ExternalFunctionConfig>()

        override fun protocol(): String = protocolName

        override fun prepare(configs: List<ExternalFunctionConfig>) {
            preparedConfigs.addAll(configs)
        }

        override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse {
            throw UnsupportedOperationException()
        }
    }

    private class InMemorySubscriber(
        private val snapshots: List<FunctionConfigSnapshot>
    ) : FunctionConfigSubscriber {
        override fun loadAll(): List<FunctionConfigSnapshot> = snapshots
        override fun get(key: String): FunctionConfigSnapshot? = snapshots.find { it.functionName == key }
        override fun watch(listener: com.fluxion.adapter.spi.config.KeyedConfigChangeListener<FunctionConfigSnapshot>) {}
    }

    @Test
    fun `init prepares external function transports after loading snapshots`() {
        val httpTransport = CapturingTransport("http")
        val dubboTransport = CapturingTransport("dubbo")
        val registry = ExternalFunctionTransportRegistry(
            transports = listOf(httpTransport, dubboTransport),
            useServiceLoader = false
        )

        val subscriber = InMemorySubscriber(
            listOf(
                FunctionConfigSnapshot(
                    functionName = "external:httpUser",
                    functionType = "EXTERNAL",
                    config = mapOf(
                        "protocol" to "http",
                        "service" to "https://api.example.com/users",
                        "method" to "GET"
                    )
                ),
                FunctionConfigSnapshot(
                    functionName = "external:dubboUser",
                    functionType = "EXTERNAL",
                    config = mapOf(
                        "protocol" to "dubbo",
                        "service" to "com.example.UserService",
                        "method" to "getUser",
                        "endpoint" to "dubbo://127.0.0.1:20880"
                    )
                ),
                FunctionConfigSnapshot(
                    functionName = "script:hello",
                    functionType = "SCRIPT_GROOVY",
                    scriptBody = "return 'hello'"
                )
            )
        )

        val applier = FunctionConfigApplier(
            subscriber = subscriber,
            registry = FunctionRegistry(),
            transportRegistry = registry
        )
        applier.init()

        assertEquals(1, httpTransport.preparedConfigs.size)
        assertEquals("https://api.example.com/users", httpTransport.preparedConfigs[0].service)

        assertEquals(1, dubboTransport.preparedConfigs.size)
        assertEquals("com.example.UserService", dubboTransport.preparedConfigs[0].service)
    }

    @Test
    fun `onChange prepares external config for publish and update`() {
        val httpTransport = CapturingTransport("http")
        val registry = ExternalFunctionTransportRegistry(
            transports = listOf(httpTransport),
            useServiceLoader = false
        )

        val applier = FunctionConfigApplier(
            subscriber = InMemorySubscriber(emptyList()),
            registry = FunctionRegistry(),
            transportRegistry = registry
        )

        val snapshot = FunctionConfigSnapshot(
            functionName = "external:httpOrder",
            functionType = "EXTERNAL",
            config = mapOf(
                "protocol" to "http",
                "service" to "https://api.example.com/orders",
                "method" to "POST"
            )
        )

        applier.onChange("external:httpOrder", snapshot, ChangeType.PUBLISH)

        assertEquals(1, httpTransport.preparedConfigs.size)
        assertEquals("https://api.example.com/orders", httpTransport.preparedConfigs[0].service)
    }
}
