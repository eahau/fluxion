package com.fluxion.schema.model

/**
 * Schema 统一封装。
 *
 * @property name 注册名，内联 schema 可为 null
 * @property format Schema 格式类型
 * @property raw 原始文本/JSON 字符串
 * @property parsed 编译后对象，格式相关：JSON Schema = [com.networknt.schema.JsonSchema]
 */
data class Schema(
    val name: String?,
    val format: SchemaFormat,
    val raw: String,
    val parsed: Any
) {
    /**
     * 是否为空 Schema（不校验任何数据）。
     *
     * 空 Schema 的判定标准：
     * - raw 为空白字符串
     * - raw 为 `"null"`
     * - raw 为 `"{}"`
     *
     * 空 Schema 在校验时直接通过，等价于"不做校验"。
     * 三种格式（JSON Schema / Protobuf / Avro）统一遵循此约定。
     */
    val isEmpty: Boolean
        get() {
            val trimmed = raw.trim()
            return trimmed.isBlank() || trimmed == "{}" || trimmed == "null"
        }
}
