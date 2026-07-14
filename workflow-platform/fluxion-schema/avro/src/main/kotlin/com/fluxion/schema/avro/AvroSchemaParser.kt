/**
 * Avro [SchemaParser] that compiles an Avro schema JSON document into an
 * [org.apache.avro.Schema] instance wrapped in the unified [Schema] envelope.
 *
 * Blank / null raw inputs are normalised to `"{}"` which Avro's own parser
 * rejects; we map that case to an empty marker that validators treat as
 * "any value is valid" — mirroring JSON Schema semantics.
 */
package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Compiles Avro JSON schema text into the runtime [Schema] model.
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
            log.error(e) { "Failed to parse Avro schema: ${e.message}" }
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
     * Normalise the raw input so blank/null/empty forms map to the JSON
     * object `"{}"` — an input convention shared with the JSON Schema and
     * Protobuf parsers.
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }
}
