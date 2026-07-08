package com.fluxion.schema.protobuf

import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.json.JsonSchemaCodec
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.SchemaFormat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Protobuf Schema 鍏ㄩ摼璺泦鎴愭祴璇曘€?
 *
 * 瑕嗙洊锛歅arser 鈫?Validator 鈫?FieldExtractor 鈫?Codec 鈫?DefaultSchemaManager 澶氭牸寮忚矾鐢便€?
 * 浣跨敤 FileDescriptorProto JSON 鏍煎紡锛坧rotoc --descriptor_set_out --json_format 杈撳嚭锛夈€?
 */
class ProtobufSchemaIntegrationTest {

    private val parser = ProtobufSchemaParser()
    private val validator = ProtobufSchemaValidator()
    private val extractor = ProtobufSchemaFieldExtractor()
    private val codec = ProtobufSchemaCodec()

    // FileDescriptorProto JSON 鏍煎紡锛堝姩鎬?Schema锛屾棤闇€ .proto 缂栬瘧锛?
    private val sampleDescriptorJson = """
    {
      "name": "user.proto",
      "package": "com.fluxion.test",
      "messageType": [
        {
          "name": "User",
          "field": [
            {"name": "id", "number": 1, "type": "TYPE_INT32", "label": "LABEL_REQUIRED"},
            {"name": "name", "number": 2, "type": "TYPE_STRING", "label": "LABEL_REQUIRED"},
            {"name": "email", "number": 3, "type": "TYPE_STRING", "label": "LABEL_OPTIONAL"},
            {"name": "age", "number": 4, "type": "TYPE_INT32", "label": "LABEL_OPTIONAL"}
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `parse Protobuf FileDescriptorProto JSON`() {
        val schema = parser.parse("user.proto", sampleDescriptorJson)
        assertThat(schema.format).isEqualTo(SchemaFormat.PROTOBUF)
        assertThat(schema.name).isEqualTo("user.proto")
        assertThat(schema.parsed).isInstanceOf(
            com.google.protobuf.DescriptorProtos.FileDescriptorProto::class.java
        )
    }

    @Test
    fun `validate data against Protobuf schema (structure check)`() {
        val schema = parser.parse(null, sampleDescriptorJson)
        val data = mapOf("id" to 1, "name" to "Alice", "email" to "alice@test.com")
        val result = validator.validate(schema, data)
        // 楠ㄦ灦鏍￠獙锛氱粨鏋勫悎娉曞嵆閫氳繃
        assertThat(result.valid).isTrue()
    }

    @Test
    fun `validate empty descriptor fails`() {
        val emptyDescriptor = """
        {
          "name": "empty.proto",
          "messageType": []
        }
        """.trimIndent()
        val schema = parser.parse(null, emptyDescriptor)
        val result = validator.validate(schema, mapOf("id" to 1))
        assertThat(result.valid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("no message type") }
    }

    @Test
    fun `extract fields from Protobuf message`() {
        val schema = parser.parse(null, sampleDescriptorJson)
        val fields = extractor.extractFields(schema)

        assertThat(fields).hasSize(4)
        assertThat(fields.map { it.name }).containsExactly("id", "name", "email", "age")

        val idField = fields.first { it.name == "id" }
        assertThat(idField.type).isEqualTo(FieldType.INTEGER)
        assertThat(idField.required).isTrue()
        assertThat(idField.metadata["fieldNumber"]).isEqualTo(1)
        assertThat(idField.metadata["protobufType"]).isEqualTo("TYPE_INT32")

        val emailField = fields.first { it.name == "email" }
        assertThat(emailField.type).isEqualTo(FieldType.STRING)
        assertThat(emailField.required).isFalse()
    }

    @Test
    fun `codec JSON serialization round-trip`() {
        val schema = parser.parse(null, sampleDescriptorJson)
        val data = mapOf("id" to 42, "name" to "Bob")

        val json = codec.toJson(data, schema)
        assertThat(json).contains("Bob")

        val decoded = codec.fromJson(json, schema)
        assertThat(decoded).isNotNull()
    }

    @Test
    fun `DefaultSchemaManager routes Protobuf format correctly`() {
        val manager = DefaultSchemaManager(
            bundles = listOf(
                SchemaFormatBundle(
                    format = SchemaFormat.JSON_SCHEMA,
                    parser = JsonSchemaParser(),
                    validator = JsonSchemaValidator(),
                    extractor = JsonSchemaFieldExtractor(),
                    codec = JsonSchemaCodec()
                ),
                SchemaFormatBundle(
                    format = SchemaFormat.PROTOBUF,
                    parser = ProtobufSchemaParser(),
                    validator = ProtobufSchemaValidator(),
                    extractor = ProtobufSchemaFieldExtractor(),
                    codec = ProtobufSchemaCodec()
                )
            )
        )

        val schema = manager.parse(SchemaFormat.PROTOBUF, sampleDescriptorJson)
        assertThat(schema.format).isEqualTo(SchemaFormat.PROTOBUF)

        val result = manager.validate(schema, mapOf("id" to 1, "name" to "Test"))
        assertThat(result.valid).isTrue()

        val fields = manager.extractFields(schema)
        assertThat(fields).hasSize(4)

        val pbCodec = manager.codec(SchemaFormat.PROTOBUF)
        assertThat(pbCodec).isInstanceOf(ProtobufSchemaCodec::class.java)
    }
}
