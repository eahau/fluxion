package com.fluxion.schema.json

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonSchemaDataProviderTest {

    private val provider = JsonSchemaDataProvider()

    @Test
    fun `getField from Map`() {
        val data = mapOf("name" to "Alice", "age" to 30)
        assertEquals("Alice", provider.getField(data, "name"))
        assertEquals(30, provider.getField(data, "age"))
        assertNull(provider.getField(data, "missing"))
    }

    @Test
    fun `getField returns null for non-Map`() {
        assertNull(provider.getField("not a map", "field"))
    }

    @Test
    fun `hasField`() {
        val data = mapOf("a" to 1)
        assertTrue(provider.hasField(data, "a"))
        assertFalse(provider.hasField(data, "b"))
        assertFalse(provider.hasField("string", "a"))
    }

    @Test
    fun `toMap`() {
        val data = mapOf("x" to 1, "y" to 2)
        assertEquals(data, provider.toMap(data))
        assertEquals(emptyMap<String, Any?>(), provider.toMap("string"))
    }

    @Test
    fun `getFieldNames`() {
        val data = mapOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(setOf("a", "b", "c"), provider.getFieldNames(data))
        assertEquals(emptySet<String>(), provider.getFieldNames("string"))
    }
}
