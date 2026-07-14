package com.fluxion.core.model

import com.fluxion.core.exception.InvalidParamException
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.schema.model.SchemaFormat

/**
 * Input context for workflow node execution. Aggregates multiple input sources:
 * direct node input, declared dependencies, workflow-level input, and node parameters.
 */
data class NodeInput(
    /** Direct input passed to this specific node - the primary data source for DAG execution. */
    val directInput: Any?,
    /** Outputs from upstream nodes that this node depends on (populated via dependsOn). */
    val declaredDeps: Map<String, Any> = emptyMap(),
    /** Global workflow input available to all nodes in the workflow. */
    val workflowInput: Map<String, Any> = emptyMap(),
    /** Static parameters defined in WorkflowNode.params - configuration that doesn't change per execution. */
    val nodeParams: Map<String, Any> = emptyMap(),
    /** Execution metadata containing executionId, traceId, and other runtime context. */
    val meta: ExecutionMeta? = null,
    /**
     * Schema format identifier for the input data - e.g., "json-schema", "protobuf", "avro".
     *
     * When null, input is treated as raw Map. When present along with [providerRegistry],
     * the input data is wrapped with Schema-backed access for type-safe field access.
     */
    val schemaFormat: String? = null,
    /** Schema name reference for shared schemas registered in SchemaRegistry. */
    val schemaName: String? = null,
    /** Version of the schema (for schema evolution support). */
    val schemaVersion: Long? = null,
    /**
     * Registry of SchemaDataProvider implementations keyed by format.
     *
     * Required when [schemaFormat] is specified to enable schema-aware field access.
     * Providers are resolved based on format:
     * - JSON: [com.fluxion.schema.json.JsonSchemaDataProvider]
     * - Protobuf: ProtobufSchemaDataProvider from fluxion-schema:protobuf module
     * - Avro: AvroSchemaDataProvider from fluxion-schema:avro module
     */
    val providerRegistry: SchemaDataProviderRegistry? = null
) {
    companion object {
        /** Regex pattern for `${variable}` template interpolation. */
        private val DOLLAR_BRACE_PATTERN = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_.]*)}""")
    }

    // ===== Direct Input Accessors =====
    // These methods provide typed access to the directInput field with various fallback strategies.

    /** Get directInput cast to T, returns null if type mismatch or input is null. */
    inline fun <reified T> input(): T? = directInput as? T

    /** Get directInput cast to T, throws InvalidParamException if null or type mismatch. */
    inline fun <reified T> requireInput(): T =
        input<T>() ?: throw InvalidParamException("directInput is required")

    /** Get directInput cast to T, returns default if null or type mismatch. */
    inline fun <reified T> input(default: T): T = input<T>() ?: default

    /** Get directInput transformed via mapper function. */
    inline fun <reified T> input(mapper: (Any) -> T): T? = directInput?.let(mapper)

    /** Get directInput transformed via mapper function with default fallback. */
    inline fun <reified T> input(default: T, mapper: (Any) -> T): T =
        input(mapper) ?: default

    fun inputAsString(default: String = ""): String = input(default) { it.toString() }
    fun inputAsInt(default: Int = 0): Int = input(default, ::convertToInt)
    fun inputAsLong(default: Long = 0L): Long = input(default, ::convertToLong)
    fun inputAsBoolean(default: Boolean = false): Boolean = input(default, ::convertToBoolean)

    // ===== Schema-Aware Input Views =====
    // These methods wrap directInput with Schema-backed access when schema information is available.

    /**
     * Resolved schema format enum from [schemaFormat] string. Returns null if format is null or unknown.
     */
    val resolvedSchemaFormat: SchemaFormat?
        get() = schemaFormat?.let { SchemaFormat.fromCode(it) }

    /**
     * Schema-aware view of directInput as Map<String, Any?>.
     *
     * - If [schemaFormat] and [providerRegistry] are available, wraps with [SchemaBackedMap]
     *   that provides type-safe field access through [com.fluxion.schema.api.SchemaDataProvider].
     *   Supports `get()`, `containsKey()`, iteration, and `toMap()` for full conversion.
     * - If no schema information, falls back to raw `Map<String, Any?>` cast.
     * - If directInput is null, returns empty map.
     *
     * ```kotlin
     * val name = nodeInput.directInputView["name"]
     * val hasAge = nodeInput.directInputView.containsKey("age")
     * nodeInput.directInputView.forEach { (k, v) -> ... }
     * ```
     */
    val directInputView: Map<String, Any?> by lazy {
        val data = directInput ?: return@lazy emptyMap<String, Any?>()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            SchemaBackedMap(data, registry.getProvider(format))
        } else {
            @Suppress("UNCHECKED_CAST")
            (data as? Map<String, Any?>) ?: emptyMap()
        }
    }

    /**
     * Schema-aware view of a declared dependency output as Map<String, Any?>.
     *
     * Similar to [directInputView], but operates on declaredDeps[nodeId]. Handles different
     * data formats including JSON Map, Protobuf DynamicMessage, and Avro GenericRecord.
     * When schema information is available, wraps with appropriate SchemaDataProvider.
     *
     * @param nodeId ID of the upstream node whose output to access
     * @return Map view of the dependency output, empty if not found
     */
    fun depView(nodeId: String): Map<String, Any?> {
        val data = declaredDeps[nodeId] ?: return emptyMap()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            return SchemaBackedMap(data, registry.getProvider(format))
        }
        @Suppress("UNCHECKED_CAST")
        return (data as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * Get a single field value from directInput via schema-aware view.
     */
    fun inputField(field: String): Any? = directInputView[field]

    /**
     * Get a single field value from directInput, throws if missing.
     */
    fun requireInputField(field: String): Any =
        inputField(field) ?: throw InvalidParamException("directInput.$field is required")

    /**
     * Get a single field value from directInput with default fallback.
     */
    fun inputFieldOrDefault(field: String, default: Any? = null): Any? =
        inputField(field) ?: default

    /**
     * Check if a field exists in directInput.
     */
    fun hasInputField(field: String): Boolean = directInputView.containsKey(field)

    /**
     * Get directInput as a Map<String, Any?> (schema-aware if available).
     */
    fun inputAsMap(): Map<String, Any?> = directInputView

    /**
     * Get all field names from directInput.
     */
    fun inputFieldNames(): Set<String> = directInputView.keys

    // ===== Node Parameters Accessors =====
    // These methods provide typed access to nodeParams - static configuration defined on the node.

    /** Get node parameter cast to T, returns null if missing or type mismatch. */
    inline fun <reified T> param(key: String): T? = nodeParams[key] as? T

    /** Get node parameter cast to T, throws if missing or type mismatch. */
    inline fun <reified T> requireParam(key: String): T =
        param<T>(key) ?: throw InvalidParamException("nodeParams.$key is required")

    /** Get node parameter cast to T with default fallback. */
    inline fun <reified T> param(key: String, default: T): T = param<T>(key) ?: default

    /** Get node parameter transformed via mapper function. */
    inline fun <reified T> param(key: String, mapper: (Any) -> T): T? =
        nodeParams[key]?.let(mapper)

    /** Get node parameter transformed via mapper function with default fallback. */
    inline fun <reified T> param(key: String, default: T, mapper: (Any) -> T): T =
        param(key, mapper) ?: default

    fun paramAsString(key: String, default: String = ""): String =
        param(key, default) { it.toString() }

    fun paramAsInt(key: String, default: Int = 0): Int =
        param(key, default, ::convertToInt)

    fun paramAsLong(key: String, default: Long = 0L): Long =
        param(key, default, ::convertToLong)

    fun paramAsBoolean(key: String, default: Boolean = false): Boolean =
        param(key, default, ::convertToBoolean)

    // ===== Workflow Input Accessors =====
    // These methods provide typed access to workflowInput - global input available to all nodes.

    /** Get workflow input value cast to T, returns null if missing or type mismatch. */
    inline fun <reified T> wfInput(key: String): T? = workflowInput[key] as? T

    /** Get workflow input value cast to T, throws if missing or type mismatch. */
    inline fun <reified T> requireWfInput(key: String): T =
        wfInput<T>(key) ?: throw InvalidParamException("workflowInput.$key is required")

    /** Get workflow input value cast to T with default fallback. */
    inline fun <reified T> wfInput(key: String, default: T): T = wfInput<T>(key) ?: default

    /** Get workflow input value transformed via mapper function. */
    inline fun <reified T> wfInput(key: String, mapper: (Any) -> T): T? =
        workflowInput[key]?.let(mapper)

    /** Get workflow input value transformed via mapper function with default fallback. */
    inline fun <reified T> wfInput(key: String, default: T, mapper: (Any) -> T): T =
        wfInput(key, mapper) ?: default

    /** Get workflow input as String. */
    fun wfInputAsString(key: String, default: String = ""): String =
        wfInput(key, default) { it.toString() }

    /** Get workflow input as Int. */
    fun wfInputAsInt(key: String, default: Int = 0): Int =
        wfInput(key, default, ::convertToInt)

    /** Get workflow input as Long. */
    fun wfInputAsLong(key: String, default: Long = 0L): Long =
        wfInput(key, default, ::convertToLong)

    /** Get workflow input as Boolean. */
    fun wfInputAsBoolean(key: String, default: Boolean = false): Boolean =
        wfInput(key, default, ::convertToBoolean)

    // ===== Declared Dependencies Accessors =====
    // These methods provide typed access to declaredDeps - outputs from upstream nodes.

    /** Get dependency output cast to T, returns null if missing or type mismatch. */
    inline fun <reified T> dep(nodeId: String): T? = declaredDeps[nodeId] as? T

    /** Get dependency output cast to T, throws if missing or type mismatch. */
    inline fun <reified T> requireDep(nodeId: String): T =
        dep<T>(nodeId) ?: throw InvalidParamException("declaredDeps.$nodeId is required")

    /** Get dependency output cast to T with default fallback. */
    inline fun <reified T> dep(nodeId: String, default: T): T = dep<T>(nodeId) ?: default

    /** Get dependency output transformed via mapper function. */
    inline fun <reified T> dep(nodeId: String, mapper: (Any) -> T): T? =
        declaredDeps[nodeId]?.let(mapper)

    /** Get dependency output transformed via mapper function with default fallback. */
    inline fun <reified T> dep(nodeId: String, default: T, mapper: (Any) -> T): T =
        dep(nodeId, mapper) ?: default

    /** Get dependency output as String. */
    fun depAsString(nodeId: String, default: String = ""): String =
        dep(nodeId, default) { it.toString() }

    /** Get dependency output as Int. */
    fun depAsInt(nodeId: String, default: Int = 0): Int =
        dep(nodeId, default, ::convertToInt)

    /** Get dependency output as Long. */
    fun depAsLong(nodeId: String, default: Long = 0L): Long =
        dep(nodeId, default, ::convertToLong)

    /** Get dependency output as Boolean. */
    fun depAsBoolean(nodeId: String, default: Boolean = false): Boolean =
        dep(nodeId, default, ::convertToBoolean)

    // ===== Schema-Aware Dependency Field Access =====

    /**
     * Get a single field value from a dependency output via schema-aware view.
     */
    fun depField(nodeId: String, field: String): Any? = depView(nodeId)[field]

    /**
     * Get all field names from a dependency output.
     */
    fun depFieldNames(nodeId: String): Set<String> = depView(nodeId).keys

    /**
     * Get a single field value from a dependency output, throws if missing.
     */
    fun requireDepField(nodeId: String, field: String): Any =
        depField(nodeId, field) ?: throw InvalidParamException("declaredDeps.$nodeId.$field is required")

    // ===== Value Resolution & Binding =====
    // These methods handle structured binding resolution for expressions like `${variable}` and `$ref`.

    /**
     * Resolve a value that may contain template variables or $ref references.
     *
     * Supports three resolution modes:
     * 1. String templates with `${variable}` syntax - interpolates variables from available sources.
     * 2. Map objects with `$ref` key - resolves references using dot notation.
     * 3. Other types - converted to string directly.
     *
     * `$ref` resolution:
     * - `input.{field}` - resolves from workflowInput
     * - `{nodeId}.{field}` - resolves from declaredDeps[nodeId]
     * - `{variable}` - searches across workflowInput, directInput, nodeParams
     *
     * Examples:
     * ```json
     * "user:${username}:${password}"                        // template interpolation
     * { "$ref": "input.userId" }                             // ref to workflow input
     * { "$ref": "n2.username" }                              // ref to dependency output
     * { "$ref": "n2.username", "prefix": "user:" }          // ref with prefix
     * { "$ref": "n2.username", "prefix": "user:${env}:" }   // prefix with interpolation
     * ```
     */
    fun resolveBinding(value: Any?): String? {
        if (value == null) return null

        // Mode 1: String template with ${var} interpolation
        if (value is String) return interpolateTemplate(value)

        // Mode 2: Map with $ref key
        if (value is Map<*, *>) {
            val ref = value["\$ref"]?.toString()
                ?: return null
            val resolved: Any? = when {
                ref.startsWith("input.") -> {
                    val field = ref.removePrefix("input.")
                    workflowInput[field]
                }
                ref.contains('.') -> {
                    val dot = ref.indexOf('.')
                    val nodeId = ref.substring(0, dot)
                    val field = ref.substring(dot + 1)
                    depView(nodeId)[field]
                }
                else -> lookupVariable(ref)
            }

            val resolvedStr = resolved?.toString() ?: return null
            val prefix = interpolateTemplate(value["prefix"]?.toString() ?: "")
            val suffix = interpolateTemplate(value["suffix"]?.toString() ?: "")
            return "$prefix$resolvedStr$suffix"
        }

        // Mode 3: Other types - convert to string
        return value.toString()
    }

    /**
     * Interpolate `${variable}` template expressions in a string.
     *
     * Variables are resolved from: workflowInput, directInput (if Map), nodeParams, and declaredDeps.
     * Unknown variables remain unchanged in the output.
     */
    private fun interpolateTemplate(template: String): String {
        if (!template.contains("\${")) return template
        return DOLLAR_BRACE_PATTERN.replace(template) { match ->
            val varName = match.groupValues[1]
            lookupVariable(varName)?.toString() ?: match.value
        }
    }

    /** Look up a variable from all available sources. */
    private fun lookupVariable(name: String): Any? {
        workflowInput[name]?.let { return it }
        directInputView[name]?.let { return it }
        nodeParams[name]?.let { return it }
        if (name.contains('.')) {
            val dot = name.indexOf('.')
            val nodeId = name.substring(0, dot)
            val field = name.substring(dot + 1)
            depView(nodeId)[field]?.let { return it }
        }
        declaredDeps[name]?.let { return it }
        return null
    }

    /**
     * Resolve a list of binding values. Null results are converted to empty strings.
     */
    fun resolveBindingList(values: List<*>): List<String> =
        values.map { resolveBinding(it) ?: "" }

    // ===== Immutable Copy Methods =====
    // These methods create new NodeInput instances with updated fields (copy pattern).

    /** Create a copy with updated directInput. */
    fun withDirectInput(newDirectInput: Any?): NodeInput = copy(directInput = newDirectInput)

    /** Create a copy with updated nodeParams. */
    fun withNodeParams(newNodeParams: Map<String, Any>?): NodeInput =
        copy(nodeParams = newNodeParams?.toMap() ?: emptyMap())

    /** Create a copy with updated workflowInput. */
    fun withWorkflowInput(newWorkflowInput: Map<String, Any>?): NodeInput =
        copy(workflowInput = newWorkflowInput?.toMap() ?: emptyMap())

    /** Create a copy with updated declaredDeps. */
    fun withDeclaredDeps(newDeclaredDeps: Map<String, Any>?): NodeInput =
        copy(declaredDeps = newDeclaredDeps?.toMap() ?: emptyMap())

    /** Create a copy with schema information (format, name, version). */
    fun withSchema(
        format: String,
        name: String? = null,
        version: Long? = null
    ): NodeInput = copy(schemaFormat = format, schemaName = name, schemaVersion = version)

    /** Create a copy with SchemaDataProviderRegistry. */
    fun withProviderRegistry(registry: SchemaDataProviderRegistry): NodeInput =
        copy(providerRegistry = registry)
}

/** Convert any value to Int with type-safe handling. */
private fun convertToInt(value: Any): Int = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> throw InvalidParamException("cannot convert to Int: `$value")
}

/** Convert any value to Long with type-safe handling. */
private fun convertToLong(value: Any): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    is String -> value.toLong()
    else -> throw InvalidParamException("cannot convert to Long: `$value")
}

/** Convert any value to Boolean with type-safe handling. */
private fun convertToBoolean(value: Any): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.lowercase().let {
        when (it) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> throw InvalidParamException("cannot convert to Boolean: `$value")
        }
    }
    else -> throw InvalidParamException("cannot convert to Boolean: `$value")
}