package com.fluxion.schema.model

/**
 * 跨格式统一的字段描述。
 *
 * 无论是 JSON Schema、Protobuf 还是 Avro，最终都映射为同一棵字段树，
 * 供前端字段选择器、条件规则配置、参数面板等场景使用。
 *
 * @property name 字段名
 * @property type 字段类型
 * @property required 是否必填
 * @property description 字段说明
 * @property format 格式约束（如 email、date-time，主要用于 JSON Schema）
 * @property nestedFields 嵌套字段（object / array item / message / record）
 * @property metadata 各格式私有属性，如 enum、pattern、protobuf field number 等
 */
data class SchemaField(
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val description: String? = null,
    val format: String? = null,
    val nestedFields: List<SchemaField> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
