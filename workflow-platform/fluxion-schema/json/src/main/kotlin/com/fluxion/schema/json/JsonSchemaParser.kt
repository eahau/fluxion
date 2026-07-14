package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.util.JsonUtil
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion

/**
 * JSON Schema parser based on networknt/json-schema-validator.
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
     * Normalize raw input: treat null/blank string/empty object uniformly as empty Schema `{}`.
     * Consistent with the empty schema behavior in [com.fluxion.core.schema.SchemaValidator].
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }
}