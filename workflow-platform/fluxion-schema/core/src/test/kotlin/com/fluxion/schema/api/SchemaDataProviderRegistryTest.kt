package com.fluxion.schema.api
import com.fluxion.schema.model.SchemaFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
class SchemaDataProviderRegistryTest {
    /* Unit test */
    private class TestSchemaDataProvider : SchemaDataProvider {
        override fun getField(data: Any, field: String): Any? = null
        override fun hasField(data: Any, field: String): Boolean = false
        override fun toMap(data: Any): Map<String, Any?> = emptyMap()
        override fun getFieldNames(data: Any): Set<String> = emptySet()
    }
    private val provider = TestSchemaDataProvider()
    @Test
    fun `registry getProvider returns correct provider`() {
        val registry = SchemaDataProviderRegistry(
            mapOf(SchemaFormat.JSON_SCHEMA to provider)
        )
        assertSame(provider, registry.getProvider(SchemaFormat.JSON_SCHEMA))
    }
    @Test
    fun `registry getProvider throws for unsupported format`() {
        val registry = SchemaDataProviderRegistry(
            mapOf(SchemaFormat.JSON_SCHEMA to provider)
        )
        val ex = assertThrows<IllegalArgumentException> {
            registry.getProvider(SchemaFormat.PROTOBUF)
        }
        assertTrue(ex.message!!.contains("protobuf"))
    }
    @Test
    fun `registry findProvider returns null for missing`() {
        val registry = SchemaDataProviderRegistry(
            mapOf(SchemaFormat.JSON_SCHEMA to provider)
        )
        assertNotNull(registry.findProvider(SchemaFormat.JSON_SCHEMA))
        assertNull(registry.findProvider(SchemaFormat.PROTOBUF))
    }
    @Test
    fun `registry hasProvider`() {
        val registry = SchemaDataProviderRegistry(
            mapOf(SchemaFormat.JSON_SCHEMA to provider)
        )
        assertTrue(registry.hasProvider(SchemaFormat.JSON_SCHEMA))
        assertFalse(registry.hasProvider(SchemaFormat.PROTOBUF))
        assertFalse(registry.hasProvider(SchemaFormat.AVRO))
    }
    @Test
    fun `EMPTY registry has no providers`() {
        val registry = SchemaDataProviderRegistry.EMPTY
        assertFalse(registry.hasProvider(SchemaFormat.JSON_SCHEMA))
        assertTrue(registry.allProviders().isEmpty())
    }
    @Test
    fun `registry allProviders returns registered providers`() {
        val registry = SchemaDataProviderRegistry(
            mapOf(SchemaFormat.JSON_SCHEMA to provider)
        )
        assertEquals(1, registry.allProviders().size)
    }
    @Test
    fun `supports custom format via Map`() {
        val customFormat = SchemaFormat("flatbuffer")
        val customProvider = object : SchemaDataProvider {
            override fun getField(data: Any, field: String): Any? = null
            override fun hasField(data: Any, field: String): Boolean = false
            override fun toMap(data: Any): Map<String, Any?> = emptyMap()
            override fun getFieldNames(data: Any): Set<String> = emptySet()
        }
        val registry = SchemaDataProviderRegistry(
            mapOf(customFormat to customProvider)
        )
        assertSame(customProvider, registry.getProvider(customFormat))
        assertTrue(registry.hasProvider(customFormat))
    }
}
