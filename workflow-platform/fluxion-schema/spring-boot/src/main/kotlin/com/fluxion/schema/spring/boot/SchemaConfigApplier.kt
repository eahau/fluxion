/**
 * Worker-side adapter that materialises schema config snapshots into the
 * local [InMemorySchemaRegistry].
 *
 * Consumes push events from [SchemaConfigSubscriber] (HTTP polling, Apollo or
 * Nacos) and, for each change, parses the raw schema via [SchemaManager]
 * (selecting the correct parser by [SchemaConfigSnapshot.schemaFormat])
 * before registering / unregistering it in the worker-local registry. This
 * is the final hop in the hot-swap pipeline:
 *
 * ```text
 * Admin WfSchemaService → SchemaConfigPublisher → config centre →
 *   SchemaConfigSubscriber → SchemaConfigApplier → InMemorySchemaRegistry
 * ```
 *
 * Private-scope schemas are filtered by [scope] so a worker only sees
 * schemas belonging to its own application group.
 */
package com.fluxion.schema.spring.boot

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.SchemaChangeListener
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.adapter.spi.config.SchemaConfigSubscriber
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.registry.InMemorySchemaRegistry
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Applies [SchemaConfigSnapshot] deltas onto an [InMemorySchemaRegistry].
 *
 * Instances are created by [FluxionSchemaAutoConfiguration] and must have
 * their [init] method invoked once after construction to seed the registry
 * from the subscriber's current view and attach the change listener.
 *
 * @param subscriber    source of schema snapshots (HTTP / Apollo / Nacos).
 * @param registry      worker-local registry to mutate on each change event.
 * @param schemaManager parser/validator facade used to compile the raw JSON.
 * @param scope         optional worker app-group filter; PRIVATE-scope schemas
 *                      whose `appGroup` does not match are silently ignored.
 */
class SchemaConfigApplier(
    private val subscriber: SchemaConfigSubscriber,
    private val registry: InMemorySchemaRegistry,
    private val schemaManager: SchemaManager,
    private val scope: String? = null
) : SchemaChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Seed the registry with the subscriber's current full snapshot set and
     * attach [this] as a change listener.
     *
     * Must be called exactly once during application startup before any
     * workflow engine tries to resolve schemas.
     */
    fun init() {
        val snapshots = subscriber.loadAll()
        for (snap in snapshots) {
            applySnapshot(snap, ChangeType.PUBLISH)
        }
        log.info { "SchemaConfigApplier initialized with ${snapshots.size} schemas" }
        subscriber.watch(this)
    }

    /**
     * React to a single schema change event forwarded by the subscriber.
     *
     * Failures are logged and swallowed — applying a bad schema must never
     * crash the worker; the previous version (if any) remains in the registry.
     */
    override fun onChange(key: String, snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        try {
            applySnapshot(snapshot, changeType)
        } catch (e: Exception) {
            log.error(e) { "Failed to apply schema config change: name=${snapshot.schemaName}, type=$changeType" }
            // Graceful degradation: keep the previous schema version intact.
        }
    }

    private fun applySnapshot(snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        val name = snapshot.schemaName

        // Scope filter: PRIVATE schemas are only visible to the worker group
        // that owns them; PUBLIC schemas are visible to every worker.
        if (snapshot.scope == "PRIVATE" && scope != null && snapshot.appGroup != scope) {
            log.debug { "Skipped private schema [$name] (appGroup=${snapshot.appGroup} not matching worker scope=$scope)" }
            return
        }

        when (changeType) {
            ChangeType.REMOVE -> {
                registry.unregister(name)
                log.info { "Removed schema [$name] from registry" }
            }
            else -> {
                if (!snapshot.enabled || snapshot.schemaJson.isNullOrBlank()) {
                    log.warn { "Skipped disabled/empty schema [$name]" }
                    return
                }

                val schemaJson: String = snapshot.schemaJson!!
                val format = parseFormat(snapshot.schemaFormat)
                val schema = schemaManager.parse(format, schemaJson)

                val registered = if (snapshot.version > 0) {
                    registry.register(schema, snapshot.version)
                } else {
                    registry.register(schema)
                    true
                }

                if (registered) {
                    log.info { "Applied schema [$name] format=${snapshot.schemaFormat} version=${snapshot.version} (changeType=$changeType)" }
                }
            }
        }
    }

    private fun parseFormat(format: String): SchemaFormat {
        return try {
            SchemaFormat.fromCode(format)
        } catch (e: Exception) {
            log.warn(e) { "Unknown schema format [$format], falling back to JSON_SCHEMA" }
            SchemaFormat.JSON_SCHEMA
        }
    }
}
