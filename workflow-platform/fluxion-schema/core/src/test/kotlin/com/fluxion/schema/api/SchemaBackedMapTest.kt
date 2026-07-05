package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SchemaBackedMapTest {

    /** 简易 mock provider：用 Map 模拟非 Map 数据对象的字段访问 */
    private val mockProvider = object : SchemaDataProvider {
        override fun getField(data: Any, field: String): Any? =
            (data as? Map<*, *>)?.get(field)

        override fun hasField(data: Any, field: String): Boolean =
            (data as? Map<*, *>)?.containsKey(field) ?: false

        override fun toMap(data: Any): Map<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return (data as? Map<String, Any?>) ?: emptyMap()
        }

        override fun getFieldNames(data: Any): Set<String> {
            @Suppress("UNCHECKED_CAST")
            return (data as? Map<String, Any?>)?.keys ?: emptySet()
        }
    }

    private val rawData = mapOf("name" to "Alice", "age" to 30, "email" to null)
    private val backedMap: Map<String, Any?> = SchemaBackedMap(rawData, mockProvider)

    // ─── get ──────────────────────────────────────────────────────

    @Test
    fun `get returns field value via provider`() {
        assertEquals("Alice", backedMap["name"])
        assertEquals(30, backedMap["age"])
    }

    @Test
    fun `get returns null for missing field`() {
        assertNull(backedMap["missing"])
    }

    @Test
    fun `get returns null for field with null value`() {
        assertNull(backedMap["email"])
        assertTrue(backedMap.containsKey("email")) // but containsKey is true
    }

    // ─── containsKey ──────────────────────────────────────────────

    @Test
    fun `containsKey delegates to provider hasField`() {
        assertTrue(backedMap.containsKey("name"))
        assertTrue(backedMap.containsKey("age"))
        assertFalse(backedMap.containsKey("missing"))
    }

    // ─── keys / size / isEmpty ────────────────────────────────────

    @Test
    fun `keys returns all field names from provider`() {
        assertEquals(setOf("name", "age", "email"), backedMap.keys)
    }

    @Test
    fun `size returns field count from provider`() {
        assertEquals(3, backedMap.size)
    }

    @Test
    fun `isEmpty returns false for non-empty data`() {
        assertFalse(backedMap.isEmpty())
    }

    @Test
    fun `isEmpty returns true for empty data`() {
        val emptyMap = SchemaBackedMap(emptyMap<String, Any?>(), mockProvider)
        assertTrue(emptyMap.isEmpty())
        assertEquals(0, emptyMap.size)
    }

    // ─── entries / values (triggers lazy toMap) ───────────────────

    @Test
    fun `entries returns all entries via toMap`() {
        val entries = backedMap.entries
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.key == "name" && it.value == "Alice" })
        assertTrue(entries.any { it.key == "age" && it.value == 30 })
    }

    @Test
    fun `values returns all values via toMap`() {
        val values = backedMap.values
        assertEquals(3, values.size)
        assertTrue(values.contains("Alice"))
        assertTrue(values.contains(30))
    }

    // ─── rawData / dataProvider ───────────────────────────────────

    @Test
    fun `rawData returns original data object`() {
        val schemaMap = SchemaBackedMap(rawData, mockProvider)
        assertSame(rawData, schemaMap.rawData())
    }

    @Test
    fun `dataProvider returns the provider`() {
        val schemaMap = SchemaBackedMap(rawData, mockProvider)
        assertSame(mockProvider, schemaMap.dataProvider())
    }

    // ─── wrap companion factory ───────────────────────────────────

    @Test
    fun `wrap with provider returns SchemaBackedMap`() {
        val result = SchemaBackedMap.wrap(rawData, mockProvider)
        assertTrue(result is SchemaBackedMap)
        assertEquals("Alice", result["name"])
    }

    @Test
    fun `wrap without provider returns original Map`() {
        val result = SchemaBackedMap.wrap(rawData, null)
        assertFalse(result is SchemaBackedMap)
        assertSame(rawData, result)
    }

    @Test
    fun `wrap without provider for non-Map returns empty`() {
        val result = SchemaBackedMap.wrap("not a map", null)
        assertTrue(result.isEmpty())
    }

    // ─── Map interop (forEach, filter, etc.) ──────────────────────

    @Test
    fun `forEach iterates all entries`() {
        val collected = mutableMapOf<String, Any?>()
        backedMap.forEach { (k, v) -> collected[k] = v }
        assertEquals(3, collected.size)
        assertEquals("Alice", collected["name"])
    }

    @Test
    fun `filter works on backed map`() {
        val filtered = backedMap.filter { it.value != null }
        assertEquals(2, filtered.size) // name + age, email is null
    }

    @Test
    fun `toMap produces standard Map copy`() {
        val standard = backedMap.toMap()
        assertFalse(standard is SchemaBackedMap)
        assertEquals("Alice", standard["name"])
        assertEquals(30, standard["age"])
    }

    // ─── equality ─────────────────────────────────────────────────

    @Test
    fun `equals with standard Map`() {
        val standard: Map<String, Any?> = mapOf("name" to "Alice", "age" to 30, "email" to null)
        assertEquals(standard, backedMap)
    }
}
