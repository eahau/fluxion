package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory

/**
 * Avro Schema 解析器。
 *
 * 将 Avro Schema JSON 文本编译为 [org.apache.avro.Schema] 对象，
 * 封装在统一的 [Schema] 容器中供后续校验和字段提取使用。
 *
 * 空 Schema（空白字符串 / "{}" / "null"）会被规范化，
 * 与 JSON Schema 的空 Schema 行为保持一致：校验时直接通过。
 */
class AvroSchemaParser : SchemaParser {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun parse(name: String?, raw: String): Schema {
        val normalized = normalizeRaw(raw)
        return try {
            val avroSchema = org.apache.avro.Schema.Parser().parse(normalized)
            Schema(
                name = name ?: avroSchema.fullName,
                format = SchemaFormat.AVRO,
                raw = normalized,
                parsed = avroSchema
            )
        } catch (e: Exception) {
            log.error("Failed to parse Avro schema: ${e.message}", e)
            throw IllegalArgumentException("Invalid Avro schema: ${e.message}", e)
        }
    }

    override fun parse(name: String?, raw: Any): Schema {
        val rawStr = when (raw) {
            is String -> raw
            is org.apache.avro.Schema -> raw.toString()
            else -> com.fluxion.schema.util.JsonUtil.serialize(raw)
        }
        return parse(name, rawStr)
    }

    /**
     * 规范化原始输入：将 null / 空字符串 / 空对象统一视为空 Schema `"{}"`，
     * 与 JSON Schema 的 empty schema 行为保持一致。
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }
}
