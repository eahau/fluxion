/**
 * Default [SchemaManager] implementation that composes format-specific SPIs
 * loaded via [SchemaFormatBundle] entries.
 *
 * The manager builds O(1) lookup maps keyed by [SchemaFormat] for each SPI
 * (parser/validator/extractor/codec) so callers never iterate the bundle
 * list on hot paths. Spring Boot auto-config assembles the bundle list from
 * the application context; plain-JVM callers pass bundles directly to the
 * constructor.
 *
 * ### Parse caching
 *
 * Schema compilation (especially JSON Schema and Protobuf descriptor
 * resolution) is expensive. The manager caches compiled `parsed` objects
 * keyed by `format.code + raw` so repeated `parse()` calls for the same
 * source text return in O(1). The cache is unbounded — appropriate for the
 * typical workload of a few hundred to a few thousand registered schemas.
 */
package com.fluxion.schema

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.api.SchemaResolver
import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [SchemaManager] backed by pluggable [SchemaFormatBundle] entries
 * and an optional [SchemaResolver] / [SchemaRegistry] for reference
 * resolution and name-based validation.
 *
 * @property bundles format-specific SPI implementations, normally collected
 *   by Spring from the application context
 * @property resolver optional reference resolver used by [resolveRef]
 * @property registry optional named-schema store used by [validateByName]
 */
class DefaultSchemaManager(
    bundles: List<SchemaFormatBundle> = emptyList(),
    private val resolver: SchemaResolver? = null,
    private val registry: SchemaRegistry? = null
) : SchemaManager {

    private val parserMap: Map<SchemaFormat, SchemaParser> =
        bundles.mapNotNull { b -> b.parser?.let { b.format to it } }.toMap()

    private val validatorMap: Map<SchemaFormat, SchemaValidator> =
        bundles.mapNotNull { b -> b.validator?.let { b.format to it } }.toMap()

    private val extractorMap: Map<SchemaFormat, SchemaFieldExtractor> =
        bundles.mapNotNull { b -> b.extractor?.let { b.format to it } }.toMap()

    private val codecMap: Map<SchemaFormat, SchemaCodec> =
        bundles.mapNotNull { b -> b.codec?.let { b.format to it } }.toMap()

    /**
     * Compiled-artifact cache keyed by `format.code + raw` text.
     *
     * Only caches the `parsed` payload rather than the full [Schema] wrapper
     * because callers may supply different names for the same raw text
     * (e.g. anonymous inline schemas vs registered named ones).
     * Thread-safe via ConcurrentHashMap; entries are never evicted.
     */
    private val parseCache = ConcurrentHashMap<Pair<String, String>, Any>()

    override fun parse(format: SchemaFormat, raw: String): Schema {
        val parser = parserMap[format]
            ?: throw IllegalArgumentException("No parser available for format: ${format.code}")

        val cacheKey = format.code to raw
        val cached = parseCache[cacheKey]
        if (cached != null) {
            return Schema(name = null, format = format, raw = raw, parsed = cached)
        }

        val schema = parser.parse(null, raw)
        parseCache[cacheKey] = schema.parsed
        return schema
    }

    override fun parseJson(raw: String): Schema = parse(SchemaFormat.JSON_SCHEMA, raw)

    override fun validate(schema: Schema, data: Any?): ValidationResult {
        val validator = validatorMap[schema.format]
            ?: throw IllegalArgumentException("No validator available for format: ${schema.format.code}")
        return validator.validate(schema, data)
    }

    override fun validateByName(schemaName: String, data: Any?): ValidationResult {
        val registry = registry ?: throw IllegalStateException("SchemaRegistry is not configured")
        val schema = registry.get(schemaName)
            ?: throw IllegalArgumentException("Schema not found: `$schemaName")
        return validate(schema, data)
    }

    override fun extractFields(schema: Schema): List<SchemaField> {
        val extractor = extractorMap[schema.format]
            ?: throw IllegalArgumentException("No extractor available for format: ${schema.format.code}")
        return extractor.extractFields(schema)
    }

    override fun resolveRef(ref: String): Schema? {
        return resolver?.resolve(ref)
    }

    override fun codec(format: SchemaFormat): SchemaCodec {
        return codecMap[format]
            ?: throw IllegalArgumentException("No codec available for format: ${format.code}")
    }

    /**
     * Clears the internal parsed-artifact cache.
     *
     * Used by tests to reset state between runs and by admin-side
     * hot-reload paths after a bulk schema-format upgrade invalidates
     * previously compiled representations.
     */
    fun clearParseCache() {
        parseCache.clear()
    }

    /** Number of compiled artifacts currently held in the parse cache. */
    fun parseCacheSize(): Int = parseCache.size
}
