/**
 * In-memory [SchemaRegistry] implementation used on worker instances.
 *
 * Supports idempotent registration (same-name overwrites guarantee the
 * newest version always wins), unregistration, and optional monotonic
 * version guards that prevent out-of-order push events from rolling a
 * schema back to an older revision.
 *
 * Thread safety is provided by `ConcurrentHashMap` for both the schema
 * storage and the version-tracking map; callers never synchronize externally.
 *
 * Structural parity with [com.fluxion.functionmeta.registry.InMemoryFunctionMetaRegistry]
 * is intentional: both registries share the same write-on-config-push,
 * read-heavy access pattern and use identical data structures.
 */
package com.fluxion.schema.registry

import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.model.Schema
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Push-only in-memory [SchemaRegistry] suitable for worker-side deployments
 * that receive schema updates from a config centre.
 */
class InMemorySchemaRegistry : SchemaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    /** schemaName → compiled [Schema] wrapper */
    private val schemas = ConcurrentHashMap<String, Schema>()

    /** schemaName → latest version number, used for out-of-order push detection */
    private val versions = ConcurrentHashMap<String, Long>()

    /**
     * Registers or overwrites a schema (idempotent: same name replaces).
     *
     * @param schema schema wrapper carrying a non-null [Schema.name]
     * @throws IllegalArgumentException if [Schema.name] is null
     */
    fun register(schema: Schema) {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")
        schemas[name] = schema
        log.debug { "Registered schema: name=$name, format=${schema.format}" }
    }

    /**
     * Registers a schema together with a monotonic version guard.
     *
     * When `version > 0` the write is only applied if it strictly exceeds
     * the currently recorded version; stale pushes are logged and skipped
     * so late-arriving events never roll a schema backwards.
     *
     * @param schema schema wrapper with a non-null name
     * @param version monotonic revision; `0` disables the version guard
     * @return `true` if the registry actually mutated
     * @throws IllegalArgumentException if [Schema.name] is null
     */
    fun register(schema: Schema, version: Long): Boolean {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")

        if (version > 0) {
            val currentVersion = versions[name] ?: 0L
            if (version <= currentVersion) {
                log.debug {
                    "Skipped outdated schema update: name=$name, incoming=$version, current=$currentVersion"
                }
                return false
            }
            versions[name] = version
        }

        schemas[name] = schema
        log.info { "Registered schema: name=$name, format=${schema.format}, version=$version" }
        return true
    }

    /**
     * Removes a named schema entry and its associated version marker.
     *
     * @param name schema name previously registered
     * @return `true` if an entry actually existed and was removed
     */
    fun unregister(name: String): Boolean {
        versions.remove(name)
        val removed = schemas.remove(name) != null
        if (removed) {
            log.info { "Unregistered schema: name=$name" }
        } else {
            log.debug { "Schema not found for unregister: name=$name" }
        }
        return removed
    }

    /** Wipes every registered entry — used on shutdown or test teardown. */
    fun clear() {
        schemas.clear()
        versions.clear()
    }

    /** Current number of registered schema entries — used by admin health endpoints. */
    fun size(): Int = schemas.size

    override fun get(name: String, version: Long?): Schema? {
        // Version pinning is intentionally ignored on workers — the registry
        // always holds exactly one "current" revision pushed by the admin.
        return schemas[name]
    }

    override fun list(): List<Schema> = schemas.values.toList()
}
