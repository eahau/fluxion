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
 * [SchemaManager] 默认实现。
 *
 * 接受 [SchemaFormatBundle] 列表，按格式自动拆分构建 O(1) 路由 Map，
 * 支持 JSON Schema、Protobuf、Avro 等多格式。
 *
 * 格式路由由 Spring 装配层通过 Bundle 注入，SPI 实现类无需感知格式。
 *
 * ### 编译缓存
 *
 * Schema 编译（尤其是 JSON Schema / Protobuf）开销较大，
 * 内部维护 `format + raw` → 编译后对象的缓存，避免重复解析。
 * 缓存无上限，适用于 Schema 数量可控的场景（通常几百到几千个）。
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
     * 编译缓存：`format.code + raw` → 编译后对象（Schema.parsed）。
     *
     * 只缓存编译产物，不缓存 Schema 整体（因为 name 可能不同）。
     * 线程安全，无上限。
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
            ?: throw IllegalArgumentException("Schema not found: $schemaName")
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
     * 清空编译缓存（测试或热更新时使用）。
     */
    fun clearParseCache() {
        parseCache.clear()
    }

    /**
     * 当前缓存的编译产物数量。
     */
    fun parseCacheSize(): Int = parseCache.size
}
