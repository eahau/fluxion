package com.fluxion.schema.api

/**
 * Schema 感知的只读 Map 视图 — 将 [SchemaDataProvider] 的字段访问能力适配为标准 [Map] 接口。
 *
 * 核心设计：`get(key)` / `containsKey(key)` 等操作直接委托给 provider，
 * 无需先调用 `provider.toMap()` 做全量转换，避免不必要的性能开销。
 *
 * ### 使用场景
 *
 * 当数据对象是 Protobuf `DynamicMessage` 或 Avro `GenericRecord` 时，
 * 用此类包装后即可像普通 Map 一样使用，也可传递给任何接受 `Map<String, Any?>` 的 API。
 *
 * ### 性能特征
 *
 * | 操作          | 代价                                          |
 * |---------------|-----------------------------------------------|
 * | get           | O(1)：直接调用 provider.getField              |
 * | containsKey   | O(1)：直接调用 provider.hasField              |
 * | keys / size   | O(n) 首次，后续 O(1)：lazy 缓存 fieldNames    |
 * | entries/values| O(n) 首次，后续 O(1)：lazy 缓存全量转换结果   |
 *
 * @param data     原始数据对象（Map / DynamicMessage / GenericRecord 等）
 * @param provider 对应的数据访问策略
 */
class SchemaBackedMap(
    private val data: Any,
    private val provider: SchemaDataProvider
) : AbstractMap<String, Any?>() {

    /**
     * 字段名集合缓存：首次访问时通过 provider 获取，后续复用。
     * 用于 keys / size / isEmpty 等操作。
     */
    private val fieldNames: Set<String> by lazy { provider.getFieldNames(data) }

    /**
     * 全量转换结果缓存：仅在调用 [entries] / [values] / [iterator] / [containsValue]
     * 等需要全量遍历的方法时触发。单纯的 get/containsKey/size 不会触发此缓存。
     */
    private val delegate: Map<String, Any?> by lazy { provider.toMap(data) }

    // ─── 核心覆写（零转换，直接委托 provider）───────────────────────

    override fun get(key: String): Any? = provider.getField(data, key)

    override fun containsKey(key: String): Boolean = provider.hasField(data, key)

    override val keys: Set<String> get() = fieldNames

    override val size: Int get() = fieldNames.size

    override fun isEmpty(): Boolean = fieldNames.isEmpty()

    // ─── 需要全量遍历的操作（委托缓存）──────────────────────────────

    override val entries: Set<Map.Entry<String, Any?>> get() = delegate.entries

    override fun containsValue(value: Any?): Boolean = delegate.containsValue(value)

    /**
     * 获取原始数据对象（未经转换）。
     *
     * 供需要直接操作底层数据（如 DynamicMessage / GenericRecord）的场景使用。
     */
    fun rawData(): Any = data

    /**
     * 获取底层的 [SchemaDataProvider]。
     */
    fun dataProvider(): SchemaDataProvider = provider

    companion object {
        /**
         * 智能包装：若 data 已是 `Map<String, Any?>` 且无需 schema 转换，直接返回原 Map。
         *
         * @param data     原始数据对象
         * @param provider SchemaDataProvider（可为 null）
         * @return SchemaBackedMap（有 provider 时）或原 Map（无 provider 时）
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
