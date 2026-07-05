package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.util.JsonUtil
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion

/**
 * JSON Schema 解析器，基于 networknt/json-schema-validator。
 */
class JsonSchemaParser : SchemaParser {

    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    private val schemaValidatorsConfig = SchemaValidatorsConfig().apply {
        formatAssertionsEnabled = true
    }

    override fun parse(name: String?, raw: String): Schema {
        val schemaJson = normalizeRaw(raw)
        val jsonSchema = schemaFactory.getSchema(schemaJson, schemaValidatorsConfig)
        return Schema(
            name = name,
            format = SchemaFormat.JSON_SCHEMA,
            raw = schemaJson,
            parsed = jsonSchema
        )
    }

    override fun parse(name: String?, raw: Any): Schema {
        val schemaJson = when (raw) {
            is String -> normalizeRaw(raw)
            else -> JsonUtil.serialize(raw)
        }
        return parse(name, schemaJson)
    }

    /**
     * 规范化原始输入：将 null / 空字符串 / 空对象统一视为空 Schema `"{}"`，
     * 与原有 [com.fluxion.core.schema.SchemaValidator] 的 empty schema 行为保持一致。
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }
}
