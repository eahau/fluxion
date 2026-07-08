package com.fluxion.adapter.spi.config

import com.fluxion.adapter.spi.registry.PublishTarget
import com.fluxion.core.value.FunctionMeta

/**
 * Configuration snapshot DTOs and change listeners for cross-module sharing.
 *
 * Defines the SPI contract between the Admin control plane (which publishes
 * configuration changes) and Worker data plane instances (which subscribe to
 * and apply those changes). Supports three resource types:
 * - Workflow definitions
 * - Function configurations (SCRIPT / EXTERNAL types)
 * - Schema definitions
 *
 * Also includes generic single-value and multi-key abstractions for other
 * dynamic configuration (e.g. idempotency, rate limiting).
 */

/**
 * Type of configuration change event delivered to subscribers.
 */
enum class ChangeType {
    PUBLISH,
    UPDATE,
    REMOVE
}

/**
 * Workflow definition snapshot — config-center transfer object.
 *
 * Carries the full workflow definition (as JSON) plus metadata needed by the
 * Worker-side [DefinitionProvider] to cache and route workflows.
 *
 * @param workflowId       Unique workflow identifier (primary key)
 * @param definitionJson   Full DAG definition serialized as JSON; null when
 *                         this is a REMOVE tombstone
 * @param version          Monotonically increasing version number
 * @param active           Whether this definition is currently active
 * @param targetGroups     Optional list of app-group filters; empty/null =
 *                         broadcast to all worker instances
 */
data class WorkflowDefinitionSnapshot(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val active: Boolean,
    val targetGroups: List<String>?
) {
    /**
     * Check whether this snapshot targets all instances (no group restriction).
     *
     * @return true when targetGroups is null or empty
     */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        /**
         * Create a REMOVE tombstone snapshot.
         *
         * @param workflowId Workflow to remove
         * @return Snapshot with null definitionJson and active=false
         */
        @JvmStatic
        fun removed(workflowId: String) = WorkflowDefinitionSnapshot(workflowId, null, 0, false, null)

        /**
         * Create a standard publish/update snapshot with no group restriction.
         *
         * @param workflowId     Workflow ID
         * @param definitionJson Full DAG JSON
         * @param version        Version number
         * @param active         Active flag
         * @return Populated snapshot
         */
        @JvmStatic
        fun of(workflowId: String, definitionJson: String, version: Int, active: Boolean) =
            WorkflowDefinitionSnapshot(workflowId, definitionJson, version, active, null)
    }
}

/**
 * Function configuration snapshot — config-center transfer object.
 *
 * Pushes function definitions (especially SCRIPT / EXTERNAL types) from Admin
 * to Worker instances. Workers register the received functions into their
 * local [com.fluxion.core.function.FunctionRegistry] to support hot-reloading
 * without a restart.
 *
 * Implements [FunctionMeta] so downstream consumers can treat snapshots
 * interchangeably with other function metadata sources.
 *
 * @param functionName       Unique function name
 * @param functionType       Function type: SCRIPT_GROOVY / EXTERNAL / CUSTOM
 * @param version            Monotonically increasing version; 0 = unspecified (backward compatible)
 * @param scriptBody         Inline script source (SCRIPT type only)
 * @param className          Fully-qualified Java/Kotlin class name (CUSTOM type)
 * @param endpoint           Remote endpoint URL/address (EXTERNAL type only)
 * @param inputSchema        Input JSON Schema string (covariant override of FunctionMeta.inputSchema: Any?)
 * @param outputSchema       Output JSON Schema string
 * @param description        Human-readable description
 * @param inputSchemaRef     Input schema reference identifier
 * @param outputSchemaRef    Output schema reference identifier
 * @param descriptions       Per-field description map
 * @param category           Function category for UI grouping
 * @param domain             Business domain tag
 * @param config             Pass-through unified config Map for EXTERNAL-type extended fields
 * @param enabled            Whether this function is enabled; false = REMOVE event
 * @param targetGroups       Optional list of app-group filters
 */
data class FunctionConfigSnapshot(
    override val functionName: String,
    override val functionType: String,
    val version: Long = 0,
    val scriptBody: String? = null,
    val className: String? = null,
    val endpoint: String? = null,
    override val inputSchema: String? = null,
    override val outputSchema: String? = null,
    override val description: String? = null,
    override val inputSchemaRef: String = "",
    override val outputSchemaRef: String = "",
    override val descriptions: Map<String, String>? = null,
    override val category: String? = null,
    override val domain: String? = null,
    val config: Map<String, Any>? = null,
    val enabled: Boolean = true,
    val targetGroups: List<String>? = null
) : FunctionMeta {

    /**
     * Backward-compatibility alias: paramSchema = inputSchema (JSON Schema string).
     */
    val paramSchema: String?
        get() = inputSchema

    /**
     * Check whether this snapshot targets all instances (no group restriction).
     *
     * @return true when targetGroups is null or empty
     */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        /**
         * Create a REMOVE tombstone snapshot for a function.
         *
         * @param functionName Function to remove
         * @return Snapshot with enabled=false
         */
        @JvmStatic
        fun removed(functionName: String) = FunctionConfigSnapshot(
            functionName = functionName,
            functionType = "CUSTOM",
            scriptBody = null,
            className = null,
            endpoint = null,
            inputSchema = null,
            outputSchema = null,
            description = null,
            config = null,
            enabled = false,
            targetGroups = null
        )
    }
}

/**
 * Schema configuration snapshot — config-center transfer object.
 *
 * Pushes Schema definitions from Admin to Worker instances. Workers register
 * the received schemas into their local SchemaRegistry to support Schema
 * hot-reloading without a restart.
 *
 * @param schemaName   Unique schema name (primary key)
 * @param schemaFormat Schema format identifier: json-schema / protobuf / avro
 * @param schemaJson   Raw Schema content (JSON-format Schema definition);
 *                     null when this is a REMOVE tombstone
 * @param version      Monotonically increasing version; 0 = unspecified
 * @param description  Human-readable description
 * @param scope        Scope: PLATFORM / PRIVATE
 * @param appGroup     Owning application group (populated when scope=PRIVATE)
 * @param enabled      Whether this schema is enabled; false = REMOVE event
 * @param targetGroups Optional list of target Worker group filters
 */
data class SchemaConfigSnapshot(
    val schemaName: String,
    val schemaFormat: String = "json-schema",
    val schemaJson: String? = null,
    val version: Long = 0,
    val description: String? = null,
    val scope: String = "PLATFORM",
    val appGroup: String? = null,
    val enabled: Boolean = true,
    val targetGroups: List<String>? = null
) {
    /**
     * Check whether this snapshot targets all instances (no group restriction).
     */
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        /**
         * Create a REMOVE tombstone snapshot.
         */
        @JvmStatic
        fun removed(schemaName: String) = SchemaConfigSnapshot(
            schemaName = schemaName,
            schemaJson = null,
            enabled = false
        )
    }
}

/**
 * Idempotency cache configuration — supports app-level defaults with
 * per-workflow overrides.
 *
 * Two-tier strategy:
 * - App-level fields ([enabled], [ttlHours], [maxSize], [spec]) provide
 *   global defaults.
 * - [workflows] provides fine-grained overrides keyed by workflowId;
 *   unspecified fields inherit the app-level default.
 *
 * @param enabled   Whether idempotency caching is enabled (app default)
 * @param ttlHours  Cache entry TTL in hours (app default)
 * @param maxSize   Maximum cache entries (app default)
 * @param spec      Optional Caffeine advanced configuration expression (app default)
 * @param workflows Per-workflow override config; key = workflowId
 */
data class IdempotencyConfig(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val maxSize: Long = 1_000,
    val spec: String? = null,
    val workflows: Map<String, WorkflowIdempotencyConfig> = emptyMap()
)

/**
 * Per-workflow idempotency cache override.
 *
 * All fields are nullable; null = inherit the corresponding app-level default
 * from [IdempotencyConfig].
 */
data class WorkflowIdempotencyConfig(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null,
    val maxSize: Long? = null,
    val spec: String? = null
)

/**
 * Single-value config change listener — generic callback for any single-value
 * configuration (e.g. [IdempotencyConfig]).
 *
 * Upper-layer business code receives the already-parsed config object.
 */
fun interface ConfigChangeListener<T> {
    /**
     * Invoked when the configuration value changes.
     *
     * @param config New configuration value
     */
    fun onChange(config: T)
}

/**
 * Multi-key config change listener — for keyed configurations
 * (e.g. workflow definitions, function configs).
 *
 * Each change event carries the key + snapshot + change type.
 */
fun interface KeyedConfigChangeListener<T> {
    /**
     * Invoked when a keyed configuration entry changes.
     *
     * @param key        Configuration key (e.g. workflowId, functionName)
     * @param config     New configuration snapshot value
     * @param changeType Type of change: PUBLISH / UPDATE / REMOVE
     */
    fun onChange(key: String, config: T, changeType: ChangeType)
}

/** Workflow definition change listener alias. */
typealias DefinitionChangeListener = KeyedConfigChangeListener<WorkflowDefinitionSnapshot>

/** Function configuration change listener alias. */
typealias FunctionChangeListener = KeyedConfigChangeListener<FunctionConfigSnapshot>

/** Schema configuration change listener alias. */
typealias SchemaChangeListener = KeyedConfigChangeListener<SchemaConfigSnapshot>

/**
 * Single-value config fetch SPI — pulls the current value from a config center
 * for one-time initial loading.
 */
interface ConfigFetcher<T> {
    /**
     * Load the current configuration value from the config center.
     *
     * @return Parsed configuration object
     */
    fun load(): T
}

/**
 * Multi-key config fetch SPI — loads all entries plus optional key lookup.
 */
interface KeyedConfigFetcher<T> {
    /**
     * Load all currently-published configuration entries.
     */
    fun loadAll(): List<T>

    /**
     * Fetch a single configuration entry by key.
     *
     * @param key Entry key
     * @return Entry value, or null if not found
     */
    fun get(key: String): T?
}

/**
 * Single-value config watch SPI — subscribes to change events and pushes
 * the updated [T] value to registered listeners.
 */
interface ConfigWatcher<T> {
    /**
     * Register a listener that receives new config values on change.
     *
     * @param listener Callback to invoke
     */
    fun watch(listener: ConfigChangeListener<T>)
}

/**
 * Multi-key config watch SPI — subscribes to keyed change events.
 */
interface KeyedConfigWatcher<T> {
    /**
     * Register a listener that receives key + snapshot + change type events.
     */
    fun watch(listener: KeyedConfigChangeListener<T>)
}

/**
 * Single-value config subscriber SPI = [ConfigFetcher] + [ConfigWatcher].
 *
 * Implemented by each config center backend (Apollo, Nacos, HTTP registry) to
 * provide both initial load and live-watch capabilities.
 */
interface ConfigSubscriber<T> : ConfigFetcher<T>, ConfigWatcher<T>

/**
 * Multi-key config subscriber SPI = [KeyedConfigFetcher] + [KeyedConfigWatcher].
 */
interface KeyedConfigSubscriber<T> : KeyedConfigFetcher<T>, KeyedConfigWatcher<T>

/** Workflow definition config subscriber alias — business instance. */
typealias DefinitionConfigSubscriber = KeyedConfigSubscriber<WorkflowDefinitionSnapshot>

/** Function config subscriber alias — Worker instance. */
typealias FunctionConfigSubscriber = KeyedConfigSubscriber<FunctionConfigSnapshot>

/** Schema config subscriber alias — Worker instance. */
typealias SchemaConfigSubscriber = KeyedConfigSubscriber<SchemaConfigSnapshot>

/** Idempotency config dynamic subscriber alias. */
typealias IdempotencyConfigSubscriber = ConfigSubscriber<IdempotencyConfig>

/**
 * Generic multi-key configuration publisher SPI — Admin-side write contract.
 *
 * Implemented by backends (Nacos, Apollo, HTTP registry) to provide unified
 * publish / unpublish operations. New configuration types only require a new
 * backend bean in AutoConfiguration — no dedicated publisher interface needed.
 *
 * Usage example (register a new keyed config publisher with Nacos backend):
 * ```kotlin
 * @Bean
 * fun myConfigPublisher(cs: ConfigService) =
 *     NacosKeyedConfigPublisher(cs, "WORKFLOW", "my.config.", "my.config.__index__")
 * ```
 */
interface KeyedConfigPublisher<T> {
    /**
     * Publish or update a configuration entry for the given key.
     *
     * @param key      Configuration key
     * @param snapshot Configuration snapshot value
     */
    fun publish(key: String, snapshot: T)

    /**
     * Remove (unpublish) a configuration entry.
     *
     * @param key Configuration key to remove
     */
    fun unpublish(key: String)
}

/**
 * Generic single-value configuration publisher SPI.
 *
 * Suitable for whole-replacement single-value configs (e.g. rate-limit rules,
 * feature flags).
 */
interface ConfigPublisher<T> {
    /**
     * Publish or overwrite the current single-value configuration.
     */
    fun publish(config: T)
}

/**
 * Workflow definition publisher SPI — Admin-side write contract.
 */
interface DefinitionConfigPublisher {
    /**
     * Publish/update a workflow definition (broadcast to all groups).
     *
     * @param workflowId     Workflow ID
     * @param definitionJson Full DAG JSON
     * @param version        Version number
     */
    fun publish(workflowId: String, definitionJson: String, version: Int)

    /**
     * Publish/update targeting a specific [PublishTarget] subset of workers.
     * Default implementation delegates to the broadcast variant.
     */
    fun publish(workflowId: String, definitionJson: String, version: Int, target: PublishTarget) {
        publish(workflowId, definitionJson, version)
    }

    /**
     * Unpublish / remove a workflow definition.
     */
    fun unpublish(workflowId: String)
}

/**
 * Function configuration publisher SPI — Admin-side write contract.
 */
interface FunctionConfigPublisher {
    /**
     * Publish/update a function configuration (broadcast).
     */
    fun publish(snapshot: FunctionConfigSnapshot)

    /**
     * Publish/update targeting a specific worker subset.
     */
    fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    /**
     * Unpublish / remove a function.
     */
    fun unpublish(functionName: String)
}

/**
 * Schema configuration publisher SPI — Admin-side write contract.
 */
interface SchemaConfigPublisher {
    /**
     * Publish/update a schema configuration (broadcast).
     */
    fun publish(snapshot: SchemaConfigSnapshot)

    /**
     * Publish/update targeting a specific worker subset.
     */
    fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    /**
     * Unpublish / remove a schema.
     */
    fun unpublish(schemaName: String)
}
