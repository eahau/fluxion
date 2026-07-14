package com.fluxion.outbound

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OutboundTransportRegistryTest {

    @Test
    fun `register and resolve transport`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val transport = mockTransport("http")

        registry.register(transport)

        assertSame(transport, registry.resolve("http"))
        assertTrue(registry.contains("http"))
        assertEquals(setOf("http"), registry.protocols())
        assertEquals(1, registry.size())
    }

    @Test
    fun `resolve is case-insensitive`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val transport = mockTransport("HTTP")

        registry.register(transport)

        assertSame(transport, registry.resolve("http"))
        assertSame(transport, registry.resolve("HTTP"))
        assertSame(transport, registry.resolve("Http"))
    }

    @Test
    fun `constructor accepts initial transports`() {
        val http = mockTransport("http")
        val grpc = mockTransport("grpc")
        val registry = OutboundTransportRegistry(
            transports = listOf(http, grpc),
            useServiceLoader = false
        )

        assertSame(http, registry.resolve("http"))
        assertSame(grpc, registry.resolve("grpc"))
        assertEquals(2, registry.size())
    }

    @Test
    fun `register overrides duplicate protocol`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val first = mockTransport("http")
        val second = mockTransport("http")

        registry.register(first)
        registry.register(second)

        assertSame(second, registry.resolve("http"))
        assertEquals(1, registry.size())
    }

    @Test
    fun `unregister transport`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val transport = mockTransport("http")
        registry.register(transport)

        val removed = registry.unregister("http")

        assertSame(transport, removed)
        assertNull(registry.resolve("http"))
        assertFalse(registry.contains("http"))
        assertEquals(0, registry.size())
    }

    @Test
    fun `resolve unknown protocol returns null`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        assertNull(registry.resolve("unknown"))
        assertFalse(registry.contains("unknown"))
        assertNull(registry.resolve(null))
        assertFalse(registry.contains(null))
    }

    @Test
    fun `loads transports from ServiceLoader`() {
        val registry = OutboundTransportRegistry(useServiceLoader = true)

        assertTrue(registry.contains("spi-mock"))
        val response = registry.resolve("spi-mock")!!.invoke(
            OutboundRequest(config = OutboundConfig(), input = null, functionRef = "test")
        )
        assertEquals("spi-loaded", response.output)
    }

    @Test
    fun `explicit transport overrides ServiceLoader transport`() {
        val explicit = mockTransport("spi-mock")
        val registry = OutboundTransportRegistry(
            transports = listOf(explicit),
            useServiceLoader = true
        )

        assertSame(explicit, registry.resolve("spi-mock"))
    }

    @Test
    fun `shutdown invokes transport shutdown and clears registry`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val http = mockTransport("http")
        val grpc = mockTransport("grpc")
        registry.register(http)
        registry.register(grpc)

        registry.shutdown()

        assertTrue(http.shutdownCalled)
        assertTrue(grpc.shutdownCalled)
        assertEquals(0, registry.size())
        assertTrue(registry.isShutdown())
    }

    @Test
    fun `shutdown is idempotent`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val transport = mockTransport("http")
        registry.register(transport)

        registry.shutdown()
        registry.shutdown()

        assertEquals(1, transport.shutdownCount)
        assertTrue(registry.isShutdown())
    }

    @Test
    fun `prepare is skipped after shutdown`() {
        val registry = OutboundTransportRegistry(useServiceLoader = false)
        val transport = mockTransport("http")
        registry.register(transport)
        registry.shutdown()

        registry.prepare(listOf(OutboundConfig(protocol = "http")))

        assertEquals(0, transport.prepareCount)
    }

    private fun mockTransport(protocol: String): MockTransport = MockTransport(protocol)

    private class MockTransport(val protocolName: String) : OutboundTransport {
        var shutdownCalled = false
        var shutdownCount = 0
        var prepareCount = 0

        override fun protocol(): String = protocolName

        override fun prepare(configs: List<OutboundConfig>) {
            prepareCount += configs.size
        }

        override fun invoke(request: OutboundRequest): OutboundResponse =
            OutboundResponse(output = null)

        override fun shutdown() {
            shutdownCalled = true
            shutdownCount++
        }
    }
}
