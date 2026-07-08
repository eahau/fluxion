package com.fluxion.schema.json

import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonSchemaValidatorTest {

    private val parser = JsonSchemaParser()
    private val validator = JsonSchemaValidator()

    private fun parse(schema: String) = parser.parse(null, schema)

    // 鈹€鈹€鈹€ null / 绌?Schema 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `empty schema returns valid`() {
        val result = validator.validate(parse("{}"), mapOf("a" to 1))
        assertTrue(result.valid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `null data with empty schema returns valid`() {
        val result = validator.validate(parse("{}"), null)
        assertTrue(result.valid)
    }

    @Test
    fun `blank string schema returns valid`() {
        val result = validator.validate(parse("  "), "anything")
        assertTrue(result.valid)
    }

    // 鈹€鈹€鈹€ 绫诲瀷鏍￠獙 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `string type validation passes`() {
        val schema = """{"type":"object","properties":{"name":{"type":"string"}}}"""
        val data = mapOf("name" to "alice")
        assertTrue(validator.validate(parse(schema), data).valid)
    }

    @Test
    fun `string type validation fails for number`() {
        val schema = """{"type":"object","properties":{"name":{"type":"string"}}}"""
        val data = mapOf("name" to 123)
        val result = validator.validate(parse(schema), data)
        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `integer type validation`() {
        val schema = """{"type":"object","properties":{"age":{"type":"integer"}}}"""
        assertTrue(validator.validate(parse(schema), mapOf("age" to 25)).valid)
        assertFalse(validator.validate(parse(schema), mapOf("age" to "young")).valid)
    }

    @Test
    fun `required field validation`() {
        val schema = """{"type":"object","required":["id"],"properties":{"id":{"type":"string"}}}"""
        assertTrue(validator.validate(parse(schema), mapOf("id" to "123")).valid)
        val result = validator.validate(parse(schema), emptyMap<String, Any>())
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("id") })
    }

    @Test
    fun `enum validation`() {
        val schema = """{"type":"object","properties":{"status":{"type":"string","enum":["ACTIVE","INACTIVE"]}}}"""
        assertTrue(validator.validate(parse(schema), mapOf("status" to "ACTIVE")).valid)
        assertFalse(validator.validate(parse(schema), mapOf("status" to "UNKNOWN")).valid)
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
        assertTrue(validator.validate(parse(schema), mapOf("user" to mapOf("name" to "bob"))).valid)
        assertFalse(validator.validate(parse(schema), mapOf("user" to emptyMap<String, Any>())).valid)
    }

    @Test
    fun `array type validation`() {
        val schema = """{"type":"object","properties":{"tags":{"type":"array","items":{"type":"string"}}}}"""
        assertTrue(validator.validate(parse(schema), mapOf("tags" to listOf("a", "b"))).valid)
    }

    // 鈹€鈹€鈹€ Map Schema 杈撳叆 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `map schema input validation`() {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf("count" to mapOf("type" to "integer")),
            "required" to listOf("count")
        )
        val result = validator.validate(parser.parse(null, schema), mapOf("count" to 10))
        assertTrue(result.valid)
    }

    // 鈹€鈹€鈹€ 涓ユ牸妯″紡 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `validate strict throws on failure`() {
        val schema = """{"type":"object","properties":{"id":{"type":"integer"}}}"""
        assertThrows(com.fluxion.schema.exception.SchemaValidationException::class.java) {
            validator.validateStrict(parse(schema), mapOf("id" to "not-an-integer"))
        }
    }

    // 鈹€鈹€鈹€ 鏍煎紡鏍￠獙 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `email format validation`() {
        val schema = """{"type":"object","properties":{"email":{"type":"string","format":"email"}}}"""
        assertTrue(validator.validate(parse(schema), mapOf("email" to "test@example.com")).valid)
        assertFalse(validator.validate(parse(schema), mapOf("email" to "not-an-email")).valid)
    }

    // 鈹€鈹€鈹€ Schema 瀵硅薄鏍￠獙 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Test
    fun `schema format is json schema`() {
        val schema = parse("""{"type":"object"}""")
        assertEquals(SchemaFormat.JSON_SCHEMA, schema.format)
        assertNotNull(schema.parsed)
    }
}
