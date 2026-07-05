package com.fluxion.core.value

/**
 * 函数元信息 — 用于注册、文档生成、Schema 校验
 *
 * 设计为不可变值对象，通过 [FunctionMeta.builder] 构造。
 * 单例语义由 [WorkflowFunction] 实现方负责（推荐存为 companion object 常量）。
 */
data class FunctionMeta(
    /** 函数引用名（如 "builtin:dbQuery"，全局唯一） */
    val name: String,
    /** 函数说明（默认语言，兼容无 i18n 场景） */
    val description: String? = null,
    /** 输入 JSON Schema（为 null 则跳过入参校验） */
    val inputSchema: Any? = null,
    /** 输出 JSON Schema（为 null 则跳过出参校验） */
    val outputSchema: Any? = null,
    /** 函数所属领域，用于前端渲染专用编辑器（如 db、http、redis、common） */
    val domain: String? = null,
    /** 多语言描述映射 { locale: description }，如 {"zh": "...", "en": "..."} */
    val descriptions: Map<String, String>? = null
) {
    /**
     * 函数元信息 Builder
     *
     * 推荐用法：
     * ```kotlin
     * FunctionMeta.builder("builtin:httpCall")
     *     .description("发起 HTTP 请求")
     *     .paramSchema("{...}")
     *     .build()
     * ```
     */
    class Builder(private val name: String) {
        private var description: String? = null
        private var inputSchema: Any? = null
        private var outputSchema: Any? = null
        private var domain: String? = null
        private var descriptions: Map<String, String>? = null

        fun description(description: String) = apply { this.description = description }
        fun paramSchema(paramSchema: Any?) = apply { this.inputSchema = paramSchema }
        fun outputSchema(outputSchema: Any?) = apply { this.outputSchema = outputSchema }
        fun domain(domain: String) = apply { this.domain = domain }
        fun descriptions(descriptions: Map<String, String>) = apply { this.descriptions = descriptions }
        fun build(): FunctionMeta = FunctionMeta(name, description, inputSchema, outputSchema, domain, descriptions)
    }

    companion object {
        /** 创建 Builder */
        @JvmStatic
        fun builder(name: String) = Builder(name)

        /** 快速构造（无 Schema，适用于内部辅助函数） */
        @JvmStatic
        fun of(name: String): FunctionMeta = FunctionMeta(name)

        /** 组合函数的元信息（andThen 使用）：输入取 first，输出取 second */
        @JvmStatic
        fun composed(first: FunctionMeta, second: FunctionMeta) = FunctionMeta(
            name = "${first.name} >> ${second.name}",
            description = "Composed: ${first.name} then ${second.name}",
            inputSchema = first.inputSchema,
            outputSchema = second.outputSchema,
            domain = first.domain ?: second.domain
        )
    }
}
