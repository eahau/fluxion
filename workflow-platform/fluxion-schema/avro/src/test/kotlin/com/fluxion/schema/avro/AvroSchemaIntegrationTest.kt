package com.fluxion.schema.avro

import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.SchemaFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Avro Schema 鍏ㄩ摼璺泦鎴愭祴璇曘€?
 *
 * 瑕嗙洊锛歅arser 鈫?Validator 鈫?FieldExtractor 鈫?Codec 鈫?DefaultSchemaManager 澶氭牸寮忚矾鐢便€?
 */
class AvroSchemaIntegrationTest {

    private val parser = AvroSchemaParser()
    private val validator = AvroSchemaValidator()
    private val extractor = AvroSchemaFieldExtractor()
    private val codec = AvroSchemaCodec()

    private val sampleSchema = """
    {
      "type": "record",
      "name": "User",
      "namespace": "com.fluxion.test",
      "fields": [
        {"name": "id", "type": "int"},
        {"name": "name", "type": "string"},
        {"name": "email", "type": ["null", "string"], "default": null},
        {"name": "age", "type": ["null", "int"], "default": null}
      ]
    }
    """.trimIndent()

    @Test
    fun `parse Avro schema successfully`() {
        val schema = parser.parse("User", sampleSchema)
        assertThat(schema.format).isEqualTo(SchemaFormat.AVRO)
        assertThat(schema.name).isEqualTo("User")
        assertThat(schema.parsed).isInstanceOf(org.apache.avro.Schema::class.java)
    }

    @Test
    fun `parse invalid Avro schema throws`() {
        assertThrows<IllegalArgumentException> {
            parser.parse(null, "not a valid avro schema")
        }
    }

    @Test
    fun `validate conforming data passes`() {
        val schema = parser.parse(null, sampleSchema)
        val data = mapOf("id" to 1, "name" to "Alice", "email" to "alice@test.com")
        val result = validator.validate(schema, data)
        assertThat(result.valid).isTrue()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `validate non-conforming data fails`() {
        val schema = parser.parse(null, sampleSchema)
        // name is missing (required field)
        val data = mapOf("id" to 1)
        val result = validator.validate(schema, data)
        assertThat(result.valid).isFalse()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun `extract fields from Avro record`() {
        val schema = parser.parse(null, sampleSchema)
        val fields = extractor.extractFields(schema)

        assertThat(fields).hasSize(4)
        assertThat(fields.map { it.name }).containsExactly("id", "name", "email", "age")

        val idField = fields.first { it.name == "id" }
        assertThat(idField.type.name).isEqualTo("INTEGER")
        assertThat(idField.required).isTrue()

        val emailField = fields.first { it.name == "email" }
        assertThat(emailField.type.name).isEqualTo("STRING")
        assertThat(emailField.required).isFalse() // nullable union
    }

    @Test
    fun `codec round-trip JSON serialization`() {
        val schema = parser.parse(null, sampleSchema)
        val data = mapOf("id" to 42, "name" to "Bob", "email" to null)

        val json = codec.toJson(data, schema)
        assertThat(json).contains("Bob")

        val decoded = codec.fromJson(json, schema)
        assertThat(decoded).isNotNull()
    }

    @Test
    fun `codec round-trip binary serialization`() {
        val schema = parser.parse(null, sampleSchema)
        val data = mapOf("id" to 7, "name" to "Carol")

        val bytes = codec.serialize(data, schema)
        assertThat(bytes).isNotEmpty()

        val decoded = codec.deserialize(bytes, schema)
        assertThat(decoded).isNotNull()
    }

    @Test
    fun `DefaultSchemaManager routes Avro format correctly`() {
        val manager = DefaultSchemaManager(
            bundles = listOf(
                SchemaFormatBundle(
                    format = SchemaFormat.JSON_SCHEMA,
                    parser = JsonSchemaParser(),
                    validator = JsonSchemaValidator(),
                    extractor = JsonSchemaFieldExtractor(),
                    codec = com.fluxion.schema.json.JsonSchemaCodec()
                ),
                SchemaFormatBundle(
                    format = SchemaFormat.AVRO,
                    parser = AvroSchemaParser(),
                    validator = AvroSchemaValidator(),
                    extractor = AvroSchemaFieldExtractor(),
                    codec = AvroSchemaCodec()
                )
            )
        )

        // Parse Avro schema via manager
        val schema = manager.parse(SchemaFormat.AVRO, sampleSchema)
        assertThat(schema.format).isEqualTo(SchemaFormat.AVRO)

        // Validate via manager
        val result = manager.validate(schema, mapOf("id" to 1, "name" to "Test"))
        assertThat(result.valid).isTrue()

        // Extract fields via manager
        val fields = manager.extractFields(schema)
        assertThat(fields).hasSize(4)

        // Codec via manager
        val avroCodec = manager.codec(SchemaFormat.AVRO)
        assertThat(avroCodec).isInstanceOf(AvroSchemaCodec::class.java)
    }
}
