package com.fluxion.schema.api

/**
 * Format-aware data access strategy SPI — provides uniform field-level
 * access across JSON Schema (Map-based), Protobuf (DynamicMessage), and
 * Avro (GenericRecord) payloads.
 *
 * One implementation per schema format, bound via [SchemaFormatBundle] and
 * selected at runtime by [SchemaDataProviderRegistry] using the schema's
 * [SchemaFormat] code. The registry lookup is O(1) — a flat Map.
 *
 * Typical workflow pattern: after a node receives `directInput`, the engine
 * selects the provider matching the node's declared input-schema format and
 * uses it for field reads, e.g.:
 * ```
 * val provider = registry.getProvider(inputSchema.format)
 * val userId = provider.getField(directInput, "userId")
 * ```
 */
interface SchemaDataProvider {

    /**
     * Reads one field from [data].
     *
     * @param data  format-native payload — Map, DynamicMessage, GenericRecord, …
     * @param field field name (dotted paths are NOT supported by this SPI;
     *               the caller walks the nested structure itself).
     * @return the field value, or `null` if absent / null in the source.
     */
    fun getField(data: Any, field: String): Any?

    /** True iff [data] contains the named field (even if the value is null). */
    fun hasField(data: Any, field: String): Boolean

    /**
     * Fully converts [data] into a plain `Map<String, Any?>`.
     *
     * Used for cross-format compatibility — JEXL evaluation, Mustache-style
     * template rendering, Jackson serialization, and other scenarios that
     * require a real Map rather than per-field access.
     *
     * NOTE: this is the expensive path; call it only when you genuinely need
     * the full map. Prefer [getField] / [hasField] for hot-path reads.
     */
    fun toMap(data: Any): Map<String, Any?>

    /** Returns the set of all field names present in [data]. */
    fun getFieldNames(data: Any): Set<String>
}
