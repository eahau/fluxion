package com.fluxion.outbound

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OutboundConfigParserTest {

    @Test
    fun `parse empty config returns defaults`() {
        val config = OutboundConfigParser.parse(null)

        assertEquals("http", config.protocol)
        assertNull(config.service)
        assertNull(config.method)
        assertNull(config.endpoint)
        assertEquals(0, config.timeoutMs)
        assertNull(config.headers)
        assertNull(config.paramMapping)
        assertNull(config.extras)
    }

    @Test
    fun `parse common fields`() {
        val config = OutboundConfigParser.parse(
            mapOf(
                "protocol" to "grpc",
                "service" to "example.Greeter",
                "method" to "SayHello",
                "endpoint" to "localhost:9090",
                "timeoutMs" to 3000,
                "headers" to mapOf("x-tenant" to "t1"),
                "paramMapping" to mapOf("name" to "user.name")
            )
        )

        assertEquals("grpc", config.protocol)
        assertEquals("example.Greeter", config.service)
        assertEquals("SayHello", config.method)
        assertEquals("localhost:9090", config.endpoint)
        assertEquals(3000, config.timeoutMs)
        assertEquals(mapOf("x-tenant" to "t1"), config.headers)
        assertEquals(mapOf("name" to "user.name"), config.paramMapping)
    }

    @Test
    fun `parse moves protocol-specific top-level fields into extras`() {
        val config = OutboundConfigParser.parse(
            mapOf(
                "protocol" to "dubbo",
                "service" to "com.example.UserService",
                "method" to "getUser",
                "version" to "1.0.0",
                "group" to "default",
                "parameterTypes" to listOf("java.lang.Long")
            )
        )

        assertEquals("dubbo", config.protocol)
        assertEquals("1.0.0", config.extraString("version"))
        assertEquals("default", config.extraString("group"))
        assertEquals(listOf("java.lang.Long"), config.extraStringList("parameterTypes"))
    }

    @Test
    fun `parse preserves user extras and does not override with top-level duplicates`() {
        val config = OutboundConfigParser.parse(
            mapOf(
                "version" to "top-level",
                "extras" to mapOf("version" to "from-extras", "customKey" to "customValue")
            )
        )

        assertEquals("from-extras", config.extraString("version"))
        assertEquals("customValue", config.extraString("customKey"))
    }

    @Test
    fun `parse flattens nested externalService config`() {
        val config = OutboundConfigParser.parse(
            mapOf(
                "description" to "old nested config",
                "externalService" to mapOf(
                    "protocol" to "dubbo",
                    "service" to "com.example.UserService",
                    "method" to "getUser",
                    "endpoint" to "dubbo://127.0.0.1:20880",
                    "timeoutMs" to 5000,
                    "extras" to mapOf("version" to "1.0.0", "group" to "default")
                )
            )
        )

        assertEquals("dubbo", config.protocol)
        assertEquals("com.example.UserService", config.service)
        assertEquals("getUser", config.method)
        assertEquals("dubbo://127.0.0.1:20880", config.endpoint)
        assertEquals(5000, config.timeoutMs)
        assertEquals("1.0.0", config.extraString("version"))
        assertEquals("default", config.extraString("group"))
    }

    @Test
    fun `parse paramMapping from json string`() {
        val config = OutboundConfigParser.parse(
            mapOf(
                "paramMapping" to "{\"remoteName\": \"user.name\"}"
            )
        )

        assertEquals(mapOf("remoteName" to "user.name"), config.paramMapping)
    }
}
