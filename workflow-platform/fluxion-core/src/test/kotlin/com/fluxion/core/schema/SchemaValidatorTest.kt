package com.fluxion.core.schema

import com.fluxion.core.exception.SchemaValidationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SchemaValidatorTest {

    private val validator = SchemaValidator()

    // ─── null / 空 Schema ───────────────────────────────────────

    @Test
    fun `null schema returns valid`() {
        val result = validator.validate(null, "anything")
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `empty object schema returns valid`() {
        val result = validator.validate(mapOf<String, Any>(), mapOf("a" to 1))
        assertTrue(result.valid)
    }

    @Test
    fun `empty string schema returns valid`() {
        val result = validator.validate("", "anything")
        assertTrue(result.valid)
    }

    @Test
    fun `blank string schema returns valid`() {
        val result = validator.validate("  ", "anything")
        assertTrue(result.valid)
    }

    // ─── 类型校验 ───────────────────────────────────────────────

    @Test
    fun `string type validation passes`() {
        val schema = """{"type":"object","properties":{"name":{"type":"string"}}}"""
        val data = mapOf("name" to "alice")
        assertTrue(validator.validate(schema, data).valid)
    }

    @Test
    fun `string type validation fails for number`() {
        val schema = """{"type":"object","properties":{"name":{"type":"string"}}}"""
        val data = mapOf("name" to 123)
        val result = validator.validate(schema, data)
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `integer type validation`() {
        val schema = """{"type":"object","properties":{"age":{"type":"integer"}}}"""
        assertTrue(validator.validate(schema, mapOf("age" to 25)).valid)
        assertFalse(validator.validate(schema, mapOf("age" to "young")).valid)
    }

    @Test
    fun `required field validation`() {
        val schema = """{"type":"object","required":["id"],"properties":{"id":{"type":"string"}}}"""
        assertTrue(validator.validate(schema, mapOf("id" to "123")).valid)
        val result = validator.validate(schema, emptyMap<String, Any>())
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("id") })
    }

    @Test
    fun `enum validation`() {
        val schema = """{"type":"object","properties":{"status":{"type":"string","enum":["ACTIVE","INACTIVE"]}}}"""
        assertTrue(validator.validate(schema, mapOf("status" to "ACTIVE")).valid)
        assertFalse(validator.validate(schema, mapOf("status" to "UNKNOWN")).valid)
    }

    @Test
    fun `nested object validation`() {
        val schema = """
        {
          "type": "object",
          "properties": {
            "user": {
              "type": "object",
              "required": ["name"],
              "properties": {
                "name": {"type": "string"}
              }
            }
          }
        }
        """.trimIndent()
        assertTrue(validator.validate(schema, mapOf("user" to mapOf("name" to "bob"))).valid)
        assertFalse(validator.validate(schema, mapOf("user" to emptyMap<String, Any>())).valid)
    }

    @Test
    fun `array type validation`() {
        val schema = """{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}}}}"""
        assertTrue(validator.validate(schema, mapOf("tags" to listOf("a", "b"))).valid)
    }

    // ─── Map Schema 输入 ────────────────────────────────────────

    @Test
    fun `map schema input works`() {
        val schema = mapOf(
            "type" to "object",
            "required" to listOf("id"),
            "properties" to mapOf("id" to mapOf("type" to "string"))
        )
        assertTrue(validator.validate(schema, mapOf("id" to "abc")).valid)
        assertFalse(validator.validate(schema, emptyMap<String, Any>()).valid)
    }

    // ─── strict mode ────────────────────────────────────────────

    @Test
    fun `validateStrict passes for valid data`() {
        val schema = """{"type":"object","required":["id"],"properties":{"id":{"type":"string"}}}"""
        assertDoesNotThrow { validator.validateStrict(schema, mapOf("id" to "123")) }
    }

    @Test
    fun `validateStrict throws for invalid data`() {
        val schema = """{"type":"object","required":["id"],"properties":{"id":{"type":"string"}}}"""
        val ex = assertThrows<SchemaValidationException> {
            validator.validateStrict(schema, emptyMap<String, Any>())
        }
        assertTrue(ex.validationErrors.isNotEmpty())
    }

    @Test
    fun `validateStrict skips null schema`() {
        assertDoesNotThrow { validator.validateStrict(null, "anything") }
    }

    @Test
    fun `validateStrict skips empty schema`() {
        assertDoesNotThrow { validator.validateStrict("{}", mapOf("a" to 1)) }
    }

    // ─── 无效 Schema 容错 ──────────────────────────────────────

    @Test
    fun `invalid schema JSON returns fail result`() {
        val result = validator.validate("not a json schema", mapOf("a" to 1))
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("internal error") })
    }
}
