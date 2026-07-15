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
 * ### Caching
 *
 * Two levels of caching are used via [SchemaCacheManager]:
 * 1. **parseCache**: Caches compiled schema objects keyed by `format.code + raw`.
 * 2. **schemaCache**: Caches named schemas loaded from [SchemaRegistry].
 *
 * Cache configuration is loaded from fluxion-config and can be updated dynamically.
 */
package com.fluxion.schema

import com.fluxion.schema.api.SchemaCacheManager
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
import com.github.benmanes.caffeine.cache.Caffeine

class DefaultSchemaManager(
    bundles: List<SchemaFormatBundle> = emptyList(),
    private val resolver: SchemaResolver? = null,
    private val registry: SchemaRegistry? = null,
    private val cacheManager: SchemaCacheManager = SchemaCacheManager()
) : SchemaManager {

    private val parserMap: Map<SchemaFormat, SchemaParser> =
        bundles.mapNotNull { b -> b.parser?.let { b.format to it } }.toMap()

    private val validatorMap: Map<SchemaFormat, SchemaValidator> =
        bundles.mapNotNull { b -> b.validator?.let { b.format to it } }.toMap()

    private val extractorMap: Map<SchemaFormat, SchemaFieldExtractor> =
        bundles.mapNotNull { b -> b.extractor?.let { b.format to it } }.toMap()

    private val codecMap: Map<SchemaFormat, SchemaCodec> =
        bundles.mapNotNull { b -> b.codec?.let { b.format to it } }.toMap()

    init {
        registry?.let { cacheManager.preloadSchemas(it) }
    }

    override fun parse(format: SchemaFormat, raw: String): Schema {
        val parser = parserMap[format]
            ?: throw IllegalArgumentException("No parser available for format: ${format.code}")

        val cached = cacheManager.getParseResult(format.code, raw)
        if (cached != null) {
            return Schema(name = null, format = format, raw = raw, parsed = cached)
        }

        val schema = parser.parse(null, raw)
        cacheManager.putParseResult(format.code, raw, schema.parsed)
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

        val cachedSchema = cacheManager.getSchema(schemaName)
        if (cachedSchema != null) {
            return validate(cachedSchema, data)
        }

        val schema = registry.get(schemaName)
            ?: throw IllegalArgumentException("Schema not found: `$schemaName`")

        cacheManager.putSchema(schemaName, schema)
        return validate(schema, data)
    }

    override fun extractFields(schema: Schema): List<SchemaField> {
        val extractor = extractorMap[schema.format]
            ?: throw IllegalArgumentException("No extractor available for format: ${schema.format.code}")
        return extractor.extractFields(schema)
    }

    override fun resolveRef(ref: String): Schema? = resolver?.resolve(ref)

    override fun codec(format: SchemaFormat): SchemaCodec {
        return codecMap[format]
            ?: throw IllegalArgumentException("No codec available for format: ${format.code}")
    }

    fun clearCache() {
        cacheManager.invalidateAll()
    }

    fun parseCacheSize(): Int = cacheManager.parseCacheSize()

    fun schemaCacheSize(): Int = cacheManager.schemaCacheSize()

    fun getCacheManager(): SchemaCacheManager = cacheManager
}
