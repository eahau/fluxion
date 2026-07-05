package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationError
import com.fluxion.schema.model.ValidationResult
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

/**
 * Avro Schema 校验器。
 *
 * 动态校验：将 Map/POJO 数据转换为 Avro GenericRecord，
 * 再通过 Binary 编解码往返验证数据是否符合 Schema。
 * 全程无需代码生成，支持运行时动态 Schema。
 */
class AvroSchemaValidator : SchemaValidator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun validate(schema: Schema, data: Any?): ValidationResult {
        require(schema.format == SchemaFormat.AVRO) {
            "AvroSchemaValidator only supports AVRO format, got ${schema.format}"
        }

        if (schema.isEmpty) {
            return ValidationResult.ok()
        }

        val avroSchema = schema.parsed as? org.apache.avro.Schema
            ?: return ValidationResult.failDetailed(listOf(
                ValidationError(path = "", message = "Schema parsed object is not an Avro Schema", errorType = "schema")
            ))

        return try {
            if (data is GenericRecord) {
                // 已是 GenericRecord，直接做 binary 往返校验
                validateViaRoundTrip(avroSchema, data)
            } else if (data is String) {
                // Avro JSON 格式（可能包含 union 包装），尝试直接解析
                validateViaJsonDecoder(avroSchema, data)
            } else {
                // Map / POJO → GenericRecord → binary 往返校验
                val record = AvroDataConverter.toGenericRecord(data, avroSchema)
                validateViaRoundTrip(avroSchema, record)
            }
        } catch (e: Exception) {
            log.warn("Avro schema validation failed: ${e.message}")
            ValidationResult.failDetailed(listOf(
                ValidationError(
                    path = extractPathFromException(e),
                    message = "Avro validation error: ${e.message}",
                    errorType = e::class.simpleName
                )
            ))
        }
    }

    /**
     * 尝试从异常消息中提取字段路径。
     * Avro 异常消息通常包含字段名，如 "Expected int, got STRING" 或 "Field xxx not found"。
     */
    private fun extractPathFromException(e: Exception): String {
        val msg = e.message ?: return ""
        // 简单提取：尝试匹配 "field 'xxx'" 或类似模式
        val fieldMatch = Regex("""field\s+'?([\w.]+)'?""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch != null) return fieldMatch.groupValues[1]
        return ""
    }

    private fun validateViaRoundTrip(schema: org.apache.avro.Schema, record: GenericRecord): ValidationResult {
        val baos = ByteArrayOutputStream()
        val writer = GenericDatumWriter<GenericRecord>(schema)
        val encoder = EncoderFactory.get().binaryEncoder(baos, null)
        writer.write(record, encoder)
        encoder.flush()

        val reader = GenericDatumReader<GenericRecord>(schema)
        val decoder = DecoderFactory.get().binaryDecoder(baos.toByteArray(), null)
        reader.read(null, decoder)
        return ValidationResult.ok()
    }

    private fun validateViaJsonDecoder(schema: org.apache.avro.Schema, json: String): ValidationResult {
        val reader = GenericDatumReader<Any>(schema)
        val decoder = DecoderFactory.get().jsonDecoder(schema, json)
        reader.read(null, decoder)
        return ValidationResult.ok()
    }
}
