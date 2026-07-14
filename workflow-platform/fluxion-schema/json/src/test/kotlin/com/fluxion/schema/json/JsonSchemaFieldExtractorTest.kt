package com.fluxion.schema.json

import com.fluxion.schema.model.FieldType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonSchemaFieldExtractorTest {

    private val parser = JsonSchemaParser()
    private val extractor = JsonSchemaFieldExtractor()

    @Test
    fun `extract simple fields`() {
        val schema = parser.parse(null, """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string", "description": "user name"},
                "age": {"type": "integer"}
              },
              "required": ["name"]
            }
        """.trimIndent())
        val fields = extractor.extractFields(schema)
        assertEquals(2, fields.size)
        val nameField = fields.first { it.name == "name" }
        assertEquals(FieldType.STRING, nameField.type)
        assertTrue(nameField.required)
        assertEquals("user name", nameField.description)
        val ageField = fields.first { it.name == "age" }
        assertEquals(FieldType.INTEGER, ageField.type)
        assertFalse(ageField.required)
    }

    @Test
    fun `extract nested object fields`() {
        val schema = parser.parse(null, """
            {
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string"}
                  }
                }
              }
            }
        """.trimIndent())
        val fields = extractor.extractFields(schema)
        val userField = fields.first { it.name == "user" }
        assertEquals(FieldType.OBJECT, userField.type)
        assertEquals(1, userField.nestedFields.size)
        assertEquals("name", userField.nestedFields.first().name)
    }

    @Test
    fun `extract array item fields`() {
        val schema = parser.parse(null, """
            {
              "type": "object",
              "properties": {
                "tags": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              }
            }
        """.trimIndent())
        val fields = extractor.extractFields(schema)
        val tagsField = fields.first { it.name == "tags" }
        assertEquals(FieldType.ARRAY, tagsField.type)
        assertEquals(1, tagsField.nestedFields.size)
        assertEquals(FieldType.STRING, tagsField.nestedFields.first().type)
    }

    @Test
    fun `metadata includes enum and ref`() {
        val schema = parser.parse(null, """
            {
              "type": "object",
              "properties": {
                "status": {
                  "type": "string",
                  "enum": ["ACTIVE", "INACTIVE"]
                },
                "profile": {
                  "${'$'}ref": "json-schema:ProfileSchema"
                }
              }
            }
        """.trimIndent())
        val fields = extractor.extractFields(schema)
        val statusField = fields.first { it.name == "status" }
        assertNotNull(statusField.metadata["enum"])
        val profileField = fields.first { it.name == "profile" }
        assertEquals("json-schema:ProfileSchema", profileField.metadata["\$ref"])
    }
}