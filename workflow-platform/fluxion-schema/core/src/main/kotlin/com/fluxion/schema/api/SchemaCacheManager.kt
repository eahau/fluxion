package com.fluxion.schema.api

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.schema.model.Schema
import com.github.benmanes.caffeine.cache.Cache
import org.slf4j.LoggerFactory
import org.slf4j.*

class SchemaCacheManager(
    private val preloadOnStartup: Boolean = true
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val parseCache: Cache<Pair<String, String>, Any> = FluxionCacheFactory.get("schema-parse")
    private val schemaCache: Cache<String, Schema> = FluxionCacheFactory.get("schema-named")

    fun getParseResult(formatCode: String, raw: String): Any? =
        parseCache.getIfPresent(formatCode to raw)

    fun putParseResult(formatCode: String, raw: String, parsed: Any) =
        parseCache.put(formatCode to raw, parsed)

    fun getSchema(name: String): Schema? =
        schemaCache.getIfPresent(name)

    fun putSchema(name: String, schema: Schema) =
        schemaCache.put(name, schema)

    fun evictSchema(name: String) =
        schemaCache.invalidate(name)

    fun invalidateAll() {
        parseCache.invalidateAll()
        schemaCache.invalidateAll()
        log.info { "All schema caches invalidated" }
    }

    fun parseCacheSize(): Int =
        parseCache.estimatedSize().toInt()

    fun schemaCacheSize(): Int =
        schemaCache.estimatedSize().toInt()

    fun preloadSchemas(registry: SchemaRegistry) {
        if (!preloadOnStartup) {
            log.debug { "Schema preload is disabled" }
            return
        }

        try {
            val schemas = registry.list()
            for (schema in schemas) {
                schema.name?.let {
                    putSchema(it, schema)
                }
            }
            log.info { "Preloaded ${schemas.size} schemas from registry" }
        } catch (e: Exception) {
            log.warn("Failed to preload schemas from registry", e)
        }
    }
}