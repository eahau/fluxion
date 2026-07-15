/**
 * In-memory [SchemaRegistry] implementation used on worker instances.
 */
package com.fluxion.schema.registry

import com.fluxion.schema.api.ExternalSchemaRegistry
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class InMemorySchemaRegistry : ExternalSchemaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    private val schemas = ConcurrentHashMap<String, Schema>()
    private val versions = ConcurrentHashMap<String, Long>()

    fun register(schema: Schema) {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")
        schemas[name] = schema
        if (log.isDebugEnabled) {
            log.debug("Registered schema: name=$name, format=${schema.format}")
        }
    }

    fun register(schema: Schema, version: Long): Boolean {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")

        if (version > 0) {
            val currentVersion = versions[name] ?: 0L
            if (version <= currentVersion) {
                if (log.isDebugEnabled) {
                    log.debug("Skipped outdated schema update: name=$name, incoming=$version, current=$currentVersion")
                }
                return false
            }
            versions[name] = version
        }

        schemas[name] = schema
        if (log.isInfoEnabled) {
            log.info("Registered schema: name=$name, format=${schema.format}, version=$version")
        }
        return true
    }

    fun unregister(name: String): Boolean {
        versions.remove(name)
        val removed = schemas.remove(name) != null
        if (removed) {
            if (log.isInfoEnabled) {
                log.info("Unregistered schema: name=$name")
            }
        } else {
            if (log.isDebugEnabled) {
                log.debug("Schema not found for unregister: name=$name")
            }
        }
        return removed
    }

    fun clear() {
        schemas.clear()
        versions.clear()
    }

    fun size(): Int = schemas.size

    override fun get(name: String, version: Long?): Schema? {
        return schemas[name]
    }

    override fun list(): List<Schema> = schemas.values.toList()

    override fun register(name: String, format: SchemaFormat, raw: String): Schema {
        val schema = Schema(name = name, format = format, raw = raw, parsed = raw)
        register(schema)
        return schema
    }

    override fun register(name: String, format: SchemaFormat, raw: String, version: Long): Schema {
        val schema = Schema(name = name, format = format, raw = raw, parsed = raw)
        register(schema, version)
        return schema
    }

    override fun update(name: String, format: SchemaFormat, raw: String): Schema {
        return register(name, format, raw)
    }

    override fun delete(name: String): Boolean {
        return unregister(name)
    }

    override fun getSubjectName(name: String, format: SchemaFormat): String {
        return "$name-${format.code}"
    }
}