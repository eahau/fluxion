package com.fluxion.schema.api

/**
 * Schema-aware read-only [Map] view over arbitrary data objects — wraps a
 * [SchemaDataProvider]'s field-level access API so Protobuf `DynamicMessage`
 * and Avro `GenericRecord` instances (which aren't Maps) can be passed to
 * any API that accepts `Map<String, Any?>` (JEXL evaluation, template
 * rendering, Jackson serialization, …) without a full upfront conversion.
 *
 * Design is lazy-by-default to avoid paying the cost of converting large
 * messages when callers only touch a handful of fields:
 *
 * | Operation | Cost |
 * |-----------|------|
 * | [get] / [containsKey] | O(1) — direct provider delegation. |
 * | [keys] / [size] / [isEmpty] | O(n) first call, O(1) after — lazy field-name cache. |
 * | [entries] / [values] / iteration | O(n) first call, O(1) after — lazy full-conversion cache. |
 *
 * Hot paths (get/containsKey/size) therefore never trigger the expensive
 * full conversion; only callers that actually iterate pay the price, and
 * only once.
 *
 * @param data     raw data object — Map, DynamicMessage, GenericRecord, etc.
 * @param provider schema-aware access strategy for [data]'s concrete type.
 */
class SchemaBackedMap(
    private val data: Any,
    private val provider: SchemaDataProvider
) : AbstractMap<String, Any?>() {

    /**
     * Cached field-name set — computed on first [keys]/[size]/[isEmpty]
     * access, reused for subsequent calls.
     */
    private val fieldNames: Set<String> by lazy { provider.getFieldNames(data) }

    /**
     * Cached full Map conversion — computed ONLY when [entries]/[values]/
     * iteration/[containsValue] force it. Simple get/containsKey/size
     * never touch this.
     */
    private val delegate: Map<String, Any?> by lazy { provider.toMap(data) }

    // ——— Fast path: zero-conversion, direct provider delegation ———

    override fun get(key: String): Any? = provider.getField(data, key)

    override fun containsKey(key: String): Boolean = provider.hasField(data, key)

    override val keys: Set<String> get() = fieldNames

    override val size: Int get() = fieldNames.size

    override fun isEmpty(): Boolean = fieldNames.isEmpty()

    // ——— Slow path: requires full iteration, delegates to lazy cache ———

    override val entries: Set<Map.Entry<String, Any?>> get() = delegate.entries

    override fun containsValue(value: Any?): Boolean = delegate.containsValue(value)

    /**
     * Returns the raw, unconverted data object.
     *
     * For callers that know the underlying type and want to operate on it
     * directly (e.g. extracting specific Protobuf fields via descriptor).
     */
    fun rawData(): Any = data

    /** Returns the provider used for schema-aware field access. */
    fun dataProvider(): SchemaDataProvider = provider

    companion object {
        /**
         * Smart factory — when [data] is already a `Map<String, Any?>` and
         * no provider is available, returns it as-is instead of wrapping.
         * This short-circuits unnecessary allocation for JSON-Schema paths
         * where the data is already a Map.
         *
         * @param data     raw data object.
         * @param provider provider, or `null` if caller has no format info.
         */
        @JvmStatic
        fun wrap(data: Any, provider: SchemaDataProvider?): Map<String, Any?> {
            if (provider == null) {
                @Suppress("UNCHECKED_CAST")
                return (data as? Map<String, Any?>) ?: emptyMap()
            }
            return SchemaBackedMap(data, provider)
        }
    }
}
