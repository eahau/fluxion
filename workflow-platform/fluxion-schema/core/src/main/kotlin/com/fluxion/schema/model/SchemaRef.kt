package com.fluxion.schema.model

/**
 * Schema 引用，用于跨 Schema 复用与运行时解析。
 *
 * 引用格式为 `<format-code>:<name>`，其中 format-code 对应 [SchemaFormat.code]：
 * - `json-schema:UserSchema` → JSON Schema 引用
 * - `protobuf:User`          → Protobuf Schema 引用
 * - `avro:User`              → Avro Schema 引用
 *
 * 任何已注册的自定义格式码（如 `flatbuffer:Msg`）均可自动识别。
 */
data class SchemaRef(
    val format: SchemaFormat,
    val name: String,
    val version: Long? = null
) {
    companion object {

        /**
         * 从引用字符串解析 [SchemaRef]。
         *
         * 格式为 `<format-code>:<name>`，通过 [SchemaFormat.fromCodeOrNull]
         * 解析格式码，自动适配内置格式与任意扩展格式。
         *
         * @param ref 引用字符串，如 `json-schema:UserSchema`
         * @return 解析后的引用，格式无法识别或缺少冒号分隔符时返回 null
         */
        @JvmStatic
        fun parse(ref: String): SchemaRef? {
            val colonIndex = ref.indexOf(':')
            if (colonIndex <= 0 || colonIndex == ref.length - 1) return null

            val formatCode = ref.substring(0, colonIndex)
            val name = ref.substring(colonIndex + 1)

            val format = SchemaFormat.fromCodeOrNull(formatCode) ?: return null
            return SchemaRef(format, name)
        }
    }
}
