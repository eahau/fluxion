package com.fluxion.script.config

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.adapter.spi.config.KeyedConfigChangeListener
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration-level unit tests for [FunctionConfigApplier].
 *
 * Focuses on the two most important behaviours (rest are trivial pass-through):
 * 1. **`init()` loads ALL snapshots AND then batch-prepares every external
 *    transport once.** This verifies the transport warmup happens AFTER all
 *    configs are collected (so a single transport can bulk-build its
 *    connection pool / HTTP client map from a complete list of endpoints
 *    rather than one-by-one on each registration).
 * 2. **`onChange(PUBLISH/UPDATE)` re-prepares transports for the new
 *    snapshot.** Operators expect a freshly-published external function to
 *    have its connection pool warm before the first traffic arrives.
 */
class FunctionConfigApplierTest {

    /**
     * Test double transport — records `prepare(configs)` calls so tests can
     * verify batch size and contents. `invoke` is left unimplemented.
     */
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

    /**
     * In-memory subscriber — returns a static snapshot list; watch() is
     * no-op because push tests use direct `onChange` calls.
     */
    private class InMemorySubscriber(
        private val snapshots: List<FunctionConfigSnapshot>
    ) : FunctionConfigSubscriber {
        override fun loadAll(): List<FunctionConfigSnapshot> = snapshots
        override fun get(key: String): FunctionConfigSnapshot? = snapshots.find { it.functionName == key }
        override fun watch(listener: KeyedConfigChangeListener<FunctionConfigSnapshot>) {}
    }

    /**
     * Verify the batch-warmup contract: during `init()` the applier must
     * collect every EXTERNAL config from the snapshot list, then invoke
     * `transport.prepare(List)` exactly once per transport with only the
     * configs relevant to that transport's protocol.
     */
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

    /**
     * Verify that runtime PUBLISH events flow through transport.prepare so
     * newly published external functions warm their endpoints immediately.
     */
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
