package com.fluxion.schema.json

import com.fluxion.schema.registry.InMemorySchemaRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FluxionSchemaResolverTest {

    @Test
    fun `resolve schema ref`() {
        val registry = InMemorySchemaRegistry()
        val schema = JsonSchemaParser().parse("UserSchema", """{"type":"object"}""")
        registry.register(schema)

        val resolver = FluxionSchemaResolver(registry)
        val resolved = resolver.resolve("json-schema:UserSchema")

        assertNotNull(resolved)
        assertEquals("UserSchema", resolved?.name)
    }

    @Test
    fun `resolve unknown schema ref returns null`() {
        val registry = InMemorySchemaRegistry()
        val resolver = FluxionSchemaResolver(registry)
        assertNull(resolver.resolve("json-schema:Unknown"))
    }

    @Test
    fun `resolve malformed ref returns null`() {
        val registry = InMemorySchemaRegistry()
        val resolver = FluxionSchemaResolver(registry)
        assertNull(resolver.resolve("no-colon-ref"))
    }
}
