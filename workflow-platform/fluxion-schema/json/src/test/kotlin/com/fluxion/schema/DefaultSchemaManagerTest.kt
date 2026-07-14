package com.fluxion.schema

import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.registry.InMemorySchemaRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DefaultSchemaManagerTest {

    private val jsonBundle = SchemaFormatBundle(
        format = SchemaFormat.JSON_SCHEMA,
        parser = JsonSchemaParser(),
        validator = JsonSchemaValidator(),
        extractor = JsonSchemaFieldExtractor()
    )

    private val manager = DefaultSchemaManager(bundles = listOf(jsonBundle))

    @Test
    fun `parse json schema`() {
        val schema = manager.parseJson("""{"type":"object"}""")
        assertEquals("json-schema", schema.format.code)
    }

    @Test
    fun `validate by schema object`() {
        val schema = manager.parseJson("""{"type":"object","properties":{"name":{"type":"string"}}}""")
        val result = manager.validate(schema, mapOf("name" to "alice"))
        assertTrue(result.valid)
    }

    @Test
    fun `validate by name`() {
        val registry = InMemorySchemaRegistry()
        val schema = manager.parseJson("""{"type":"object","properties":{"id":{"type":"integer"}}}""")
        registry.register(schema.copy(name = "UserSchema"))

        val managerWithRegistry = DefaultSchemaManager(
            bundles = listOf(jsonBundle),
            registry = registry
        )

        val result = managerWithRegistry.validateByName("UserSchema", mapOf("id" to 1))
        assertTrue(result.valid)
    }

    @Test
    fun `extract fields`() {
        val schema = manager.parseJson("""
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer"}
              },
              "required": ["name"]
            }
        """.trimIndent())
        val fields = manager.extractFields(schema)
        assertEquals(2, fields.size)
        assertTrue(fields.any { it.name == "name" && it.required })
        assertTrue(fields.any { it.name == "age" && !it.required })
    }
}
