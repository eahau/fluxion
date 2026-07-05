package com.fluxion.schema.model

/**
 * Schema 格式标识。
 *
 * 使用值类包装格式码字符串，支持核心内置格式（JSON Schema / Protobuf / Avro）
 * 的同时，允许第三方开发者通过自定义格式码扩展（如 flatbuffer、thrift 等），
 * 无需修改 core 模块的枚举定义。
 *
 * 格式码比较忽略大小写，但建议统一使用小写短横线风格：
 * - `json-schema`
 * - `protobuf`
 * - `avro`
 */
@JvmInline
value class SchemaFormat(val code: String) {

    init {
        require(code.isNotBlank()) { "SchemaFormat code must not be blank" }
    }

    companion object {
        /** JSON Schema（Draft-07 及兼容版本）。 */
        val JSON_SCHEMA = SchemaFormat("json-schema")

        /** Protobuf FileDescriptorProto JSON 表示。 */
        val PROTOBUF = SchemaFormat("protobuf")

        /** Avro Schema JSON 表示。 */
        val AVRO = SchemaFormat("avro")

        /**
         * 根据格式码解析 [SchemaFormat]。
         *
         * 对于核心内置格式，返回预定义常量以保证引用相等；
         * 对于未知格式，返回新构造的 [SchemaFormat] 实例以支持扩展。
         */
        @JvmStatic
        fun fromCode(code: String): SchemaFormat = fromCodeOrNull(code)
            ?: throw IllegalArgumentException("SchemaFormat code must not be blank")

        /**
         * 安全解析格式码，空字符串或 null 时返回 null（而非抛异常）。
         * 适用于运行时从请求头或配置中解析，容错优于 [fromCode]。
         */
        @JvmStatic
        fun fromCodeOrNull(code: String?): SchemaFormat? {
            val normalized = code?.trim()?.lowercase() ?: return null
            if (normalized.isEmpty()) return null
            return when (normalized) {
                JSON_SCHEMA.code -> JSON_SCHEMA
                PROTOBUF.code -> PROTOBUF
                AVRO.code -> AVRO
                else -> SchemaFormat(normalized)
            }
        }
    }

    override fun toString(): String = code
}
