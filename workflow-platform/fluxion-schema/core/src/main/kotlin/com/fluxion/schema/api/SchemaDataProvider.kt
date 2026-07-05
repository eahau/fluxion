package com.fluxion.schema.api

/**
 * Schema 格式感知的数据访问策略。
 *
 * 为不同 Schema 格式（JSON / Protobuf / Avro）提供统一的字段级数据访问能力。
 * 每种格式对应一个实现，由 [SchemaDataProviderRegistry] 根据格式路由选择。
 *
 * 格式路由由 [DataProviderBinding] 在 Spring 装配层声明，SPI 本身不感知格式。
 *
 * ### 典型使用场景
 *
 * 工作流节点接收到 `directInput` 后，根据 Schema 格式选择不同的数据访问方式：
 * - JSON Schema → `Map<String, Any?>` 的 `get(key)` 访问
 * - Protobuf → `DynamicMessage.getField(FieldDescriptor)` 访问
 * - Avro → `GenericRecord.get(key)` 访问
 *
 * @see SchemaDataProviderRegistry
 */
interface SchemaDataProvider {

    /**
     * 从数据对象中获取指定字段的值。
     *
     * @param data  数据对象（具体类型由格式决定：Map / DynamicMessage / GenericRecord 等）
     * @param field 字段名
     * @return 字段值，不存在时返回 null
     */
    fun getField(data: Any, field: String): Any?

    /**
     * 判断数据对象中是否存在指定字段。
     */
    fun hasField(data: Any, field: String): Boolean

    /**
     * 将数据对象转换为通用 Map 表示。
     *
     * 用于跨格式兼容（如模板渲染、JEXL 表达式求值等需要 Map 结构的场景）。
     */
    fun toMap(data: Any): Map<String, Any?>

    /**
     * 获取数据对象中所有可用字段名。
     */
    fun getFieldNames(data: Any): Set<String>
}
