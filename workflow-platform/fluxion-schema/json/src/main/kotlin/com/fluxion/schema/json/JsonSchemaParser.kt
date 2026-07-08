package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.util.JsonUtil
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion

/**
 * JSON Schema 瑙ｆ瀽鍣紝鍩轰簬 networknt/json-schema-validator銆?
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
     * 瑙勮寖鍖栧師濮嬭緭鍏ワ細灏?null / 绌哄瓧绗︿覆 / 绌哄璞＄粺涓€瑙嗕负绌?Schema `"{}"`锛?
     * 涓庡師鏈?[com.fluxion.core.schema.SchemaValidator] 鐨?empty schema 琛屼负淇濇寔涓€鑷淬€?
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }
}
