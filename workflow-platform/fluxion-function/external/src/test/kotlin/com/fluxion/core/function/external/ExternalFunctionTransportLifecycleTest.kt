package com.fluxion.core.function.external

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExternalFunctionTransportLifecycleTest {

    @Test
    fun `shutdown invokes registered hooks in reverse order`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        val order = mutableListOf<String>()

        lifecycle.onShutdown("first") { order.add("first") }
        lifecycle.onShutdown("second") { order.add("second") }
        lifecycle.onShutdown("third") { order.add("third") }

        lifecycle.shutdown()

        assertEquals(listOf("third", "second", "first"), order)
    }

    @Test
    fun `shutdown invokes cache clear hooks after shutdown hooks`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        val order = mutableListOf<String>()

        lifecycle.onShutdown("resource") { order.add("resource-released") }
        lifecycle.onClearCache("cache") { order.add("cache-cleared") }

        lifecycle.shutdown()

        assertEquals(listOf("resource-released", "cache-cleared"), order)
    }

    @Test
    fun `shutdown is idempotent`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        var count = 0
        lifecycle.onShutdown("counter") { count++ }

        lifecycle.shutdown()
        lifecycle.shutdown()

        assertEquals(1, count)
        assertTrue(lifecycle.isShutdown())
    }

    @Test
    fun `register hook after shutdown throws`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        lifecycle.shutdown()

        assertThrows(IllegalStateException::class.java) {
            lifecycle.onShutdown("too-late") {}
        }
    }

    @Test
    fun `register cache clear hook after shutdown throws`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        lifecycle.shutdown()

        assertThrows(IllegalStateException::class.java) {
            lifecycle.onClearCache("too-late") {}
        }
    }

    @Test
    fun `failed hook does not block others`() {
        val lifecycle = ExternalFunctionTransportLifecycle()
        val order = mutableListOf<String>()

        lifecycle.onShutdown("failing") { throw RuntimeException("boom") }
        lifecycle.onShutdown("second") { order.add("second") }

        lifecycle.shutdown()

        assertEquals(listOf("second"), order)
    }
}
