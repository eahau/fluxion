package com.fluxion.config.core

import com.fluxion.core.value.FunctionMeta

enum class ChangeType {
    PUBLISH,
    UPDATE,
    REMOVE
}

data class WorkflowDefinitionSnapshot(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val active: Boolean,
    val targetGroups: List<String>?
) {
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        @JvmStatic
        fun removed(workflowId: String) = WorkflowDefinitionSnapshot(workflowId, null, 0, false, null)

        @JvmStatic
        fun of(workflowId: String, definitionJson: String, version: Int, active: Boolean) =
            WorkflowDefinitionSnapshot(workflowId, definitionJson, version, active, null)
    }
}

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

    val paramSchema: String?
        get() = inputSchema

    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
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
    fun isGlobal(): Boolean = targetGroups.isNullOrEmpty()

    companion object {
        @JvmStatic
        fun removed(schemaName: String) = SchemaConfigSnapshot(
            schemaName = schemaName,
            schemaJson = null,
            enabled = false
        )
    }
}

data class IdempotencyConfig(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val maxSize: Long = 1_000,
    val spec: String? = null,
    val workflows: Map<String, WorkflowIdempotencyConfig> = emptyMap()
)

data class WorkflowIdempotencyConfig(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null,
    val maxSize: Long? = null,
    val spec: String? = null
)

fun interface ConfigChangeListener<T> {
    fun onChange(config: T)
}

fun interface KeyedConfigChangeListener<T> {
    fun onChange(key: String, config: T, changeType: ChangeType)
}

typealias DefinitionChangeListener = KeyedConfigChangeListener<WorkflowDefinitionSnapshot>

typealias FunctionChangeListener = KeyedConfigChangeListener<FunctionConfigSnapshot>

typealias SchemaChangeListener = KeyedConfigChangeListener<SchemaConfigSnapshot>

interface ConfigFetcher<T> {
    fun load(): T
}

interface KeyedConfigFetcher<T> {
    fun loadAll(): List<T>

    fun get(key: String): T?
}

interface ConfigWatcher<T> {
    fun watch(listener: ConfigChangeListener<T>)
}

interface KeyedConfigWatcher<T> {
    fun watch(listener: KeyedConfigChangeListener<T>)
}

interface ConfigSubscriber<T> : ConfigFetcher<T>, ConfigWatcher<T>

interface KeyedConfigSubscriber<T> : KeyedConfigFetcher<T>, KeyedConfigWatcher<T>

typealias DefinitionConfigSubscriber = KeyedConfigSubscriber<WorkflowDefinitionSnapshot>

typealias FunctionConfigSubscriber = KeyedConfigSubscriber<FunctionConfigSnapshot>

typealias SchemaConfigSubscriber = KeyedConfigSubscriber<SchemaConfigSnapshot>

typealias IdempotencyConfigSubscriber = ConfigSubscriber<IdempotencyConfig>

interface KeyedConfigPublisher<T> {
    fun publish(key: String, snapshot: T)

    fun unpublish(key: String)
}

interface ConfigPublisher<T> {
    fun publish(config: T)
}

interface DefinitionConfigPublisher {
    fun publish(workflowId: String, definitionJson: String, version: Int)

    fun publish(workflowId: String, definitionJson: String, version: Int, target: PublishTarget) {
        publish(workflowId, definitionJson, version)
    }

    fun unpublish(workflowId: String)
}

interface FunctionConfigPublisher {
    fun publish(snapshot: FunctionConfigSnapshot)

    fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    fun unpublish(functionName: String)
}

interface SchemaConfigPublisher {
    fun publish(snapshot: SchemaConfigSnapshot)

    fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        publish(snapshot)
    }

    fun unpublish(schemaName: String)
}