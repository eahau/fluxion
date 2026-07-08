package com.fluxion.core.value

/**
 * Bridge between the admin console's function-metadata schema and the
 * engine's in-memory [FunctionMeta] model.
 *
 * Rationale:
 *  - `fluxion-admin` persists function configuration as a loosely-typed
 *    JSON blob (`wf_function.config`) keyed by `functionName`.
 *  - `fluxion-core` works against the strongly-typed [FunctionMeta]
 *    interface (schemas, categories, descriptions, ...).
 *
 * The bridge converts in both directions so that admin snapshots and
 * runtime registrations can exchange data without a direct compile-time
 * dependency on the admin module.
 *
 * The default implementation ([DefaultFunctionMetaBridge]) is intentionally
 * conservative — it only copies well-known fields and ignores unknown keys,
 * which keeps forward-compatibility when the admin schema adds columns.
 */
interface FunctionMetaBridge {

    /**
     * Hydrate a [FunctionMeta] from the raw `wf_function.config` map.
     *
     * @param name   logical function name (used as fallback display name).
     * @param config raw configuration map; may be null when no config row
     *               exists (e.g. built-in functions registered purely in code).
     */
    fun fromConfig(name: String, config: Map<String, Any?>?): FunctionMeta

    /**
     * Round-trip a snapshot (obtained from the admin API) into a runtime
     * instance.  Implementations may defensively copy or validate.
     */
    fun fromSnapshot(snapshot: FunctionMeta): FunctionMeta
}

/**
 * Runtime description of a workflow function.
 *
 * Carries both *referenced* schemas (`inputSchemaRef` / `outputSchemaRef`
 * — resolved against `SchemaManager`) and *inline* schemas (raw JSON —
 * used by built-in functions that don't publish to the schema registry).
 * When both are present, references take precedence (inline values are
 * fallbacks for older callers).
 */
interface FunctionMeta {
    val functionName: String
    val functionType: String
    val description: String?
    /** Schema name for the input; empty string means "not declared". */
    val inputSchemaRef: String
    /** Schema name for the output; empty string means "not declared". */
    val outputSchemaRef: String
    /** Inline raw JSON schema for the input. */
    val inputSchema: Any?
    /** Inline raw JSON schema for the output. */
    val outputSchema: Any?
    /** Optional per-field description map (shown in admin tooltips). */
    val descriptions: Map<String, String>?
    /** Category tag (e.g. `INTEGRATION`, `DATA`, `FINANCE`). */
    val category: String?
    /** Domain / business-bounded context; used for UI grouping. */
    val domain: String?

    companion object {
        /** Fluent builder; default `functionType` is `BUILTIN`. */
        @JvmStatic
        fun builder(name: String) = Builder(name)

        /** Trivial inline instance for ad-hoc registrations. */
        @JvmStatic
        fun of(name: String): FunctionMeta = InlineMeta(functionName = name)

        /**
         * Produce a composed meta that represents the pipeline
         * `first output → second input`.  Schemas are taken from the
         * endpoints; the middle contract is assumed to be satisfied.
         */
        @JvmStatic
        fun composed(first: FunctionMeta, second: FunctionMeta): FunctionMeta = InlineMeta(
            functionName = "${first.functionName} >> ${second.functionName}",
            description = "Composed: ${first.functionName} then ${second.functionName}",
            inputSchemaRef = first.inputSchemaRef,
            outputSchemaRef = second.outputSchemaRef,
            inputSchema = first.inputSchema,
            outputSchema = second.outputSchema,
            domain = first.domain ?: second.domain
        )
    }

    /** Fluent builder for [FunctionMeta]. */
    class Builder(private val functionName: String) {
        private var functionType: String = "BUILTIN"
        private var description: String? = null
        private var inputSchema: Any? = null
        private var outputSchema: Any? = null
        private var inputSchemaRef: String = ""
        private var outputSchemaRef: String = ""
        private var domain: String? = null
        private var descriptions: Map<String, String>? = null
        private var category: String? = null

        fun functionType(type: String) = apply { this.functionType = type }
        fun description(description: String?) = apply { this.description = description }

        /** Alias for [inputSchema] — matches the legacy admin `paramSchema` column name. */
        fun paramSchema(paramSchema: Any?) = apply { this.inputSchema = paramSchema }

        fun outputSchema(outputSchema: Any?) = apply { this.outputSchema = outputSchema }

        /** Alias for [inputSchemaRef] — matches the legacy admin naming. */
        fun paramSchemaRef(ref: String) = apply { this.inputSchemaRef = ref }

        fun outputSchemaRef(ref: String) = apply { this.outputSchemaRef = ref }

        fun domain(domain: String?) = apply { this.domain = domain }
        fun descriptions(descriptions: Map<String, String>?) = apply { this.descriptions = descriptions }
        fun category(category: String?) = apply { this.category = category }

        fun build(): FunctionMeta = InlineMeta(
            functionName = functionName,
            functionType = functionType,
            description = description,
            inputSchemaRef = inputSchemaRef,
            outputSchemaRef = outputSchemaRef,
            inputSchema = inputSchema,
            outputSchema = outputSchema,
            descriptions = descriptions,
            category = category,
            domain = domain
        )
    }
}

/**
 * Default bridge: maps well-known keys from a raw config map into
 * [InlineMeta], and performs an identity copy for snapshots.
 *
 * Unknown keys are silently dropped; callers that need to preserve
 * experimental fields should subclass and override.
 */
class DefaultFunctionMetaBridge : FunctionMetaBridge {

    override fun fromConfig(name: String, config: Map<String, Any?>?): FunctionMeta {
        if (config == null) return InlineMeta(name)
        return InlineMeta(
            functionName = name,
            functionType = config["functionType"] as? String ?: "BUILTIN",
            description = config["description"] as? String,
            domain = config["domain"] as? String,
            inputSchemaRef = config["paramSchemaRef"] as? String ?: "",
            outputSchemaRef = config["outputSchemaRef"] as? String ?: "",
            inputSchema = config["paramSchema"],
            outputSchema = config["outputSchema"],
            descriptions = extractDescriptions(config["descriptions"]),
            category = config["category"] as? String
        )
    }

    override fun fromSnapshot(snapshot: FunctionMeta): FunctionMeta = snapshot

    @Suppress("UNCHECKED_CAST")
    private fun extractDescriptions(raw: Any?): Map<String, String>? {
        val map = raw as? Map<*, *> ?: return null
        return (map as Map<Any, Any>).mapNotNull { (k, v) ->
            if (k is String && v is String) k to v else null
        }.toMap().takeIf { it.isNotEmpty() }
    }
}

/** Simple immutable data-class holder for [FunctionMeta]. */
data class InlineMeta(
    override val functionName: String,
    override val functionType: String = "BUILTIN",
    override val description: String? = null,
    override val inputSchemaRef: String = "",
    override val outputSchemaRef: String = "",
    override val inputSchema: Any? = null,
    override val outputSchema: Any? = null,
    override val descriptions: Map<String, String>? = null,
    override val category: String? = null,
    override val domain: String? = null
) : FunctionMeta
