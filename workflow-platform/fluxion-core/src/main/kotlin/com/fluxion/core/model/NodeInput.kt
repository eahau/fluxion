package com.fluxion.core.model

import com.fluxion.core.exception.InvalidParamException
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.schema.model.SchemaFormat

/**
 * 节点的完整输入上下文 — 类比"函数的调用参数 + 可见的外部环境"
 */
data class NodeInput(
    /** 直接入参：上一节点的返回值（线性流）或 DAG 聚合结果 */
    val directInput: Any?,
    /** 显式声明的依赖节点输出（对应节点配置 dependsOn 字段） */
    val declaredDeps: Map<String, Any> = emptyMap(),
    /** 工作流原始入参（不可变） */
    val workflowInput: Map<String, Any> = emptyMap(),
    /** 节点配置参数（来自 WorkflowNode.params，非运行时数据） */
    val nodeParams: Map<String, Any> = emptyMap(),
    /** 执行元信息（executionId/traceId 等，只读） */
    val meta: ExecutionMeta? = null,
    /**
     * 数据格式标识（"json-schema" / "protobuf" / "avro"）。
     *
     * 为 null 时回退到传统 Map 访问模式（向后兼容）。
     * 与 [providerRegistry] 配合使用，驱动 Schema 感知的字段级数据访问。
     */
    val schemaFormat: String? = null,
    /** Schema 名称（可选，用于从 SchemaRegistry 查找完整 Schema 定义） */
    val schemaName: String? = null,
    /** Schema 版本号（可选，null 表示最新版本） */
    val schemaVersion: Long? = null,
    /**
     * Schema 数据访问提供者注册表。
     *
     * 与 [schemaFormat] 配合，在运行时动态选择对应格式的数据访问策略：
     * - JSON → [com.fluxion.schema.json.JsonSchemaDataProvider]
     * - Protobuf → ProtobufSchemaDataProvider（fluxion-schema:protobuf 模块）
     * - Avro → AvroSchemaDataProvider（fluxion-schema:avro 模块）
     */
    val providerRegistry: SchemaDataProviderRegistry? = null
) {
    companion object {
        /** `${variable}` 模板占位符正则 */
        private val DOLLAR_BRACE_PATTERN = Regex("""\$\{([a-zA-Z_][a-zA-Z0-9_.]*)}""")
    }

    // ═══════════════════════════════════════════════════════════════
    // 直接入参（directInput）安全访问
    // ═══════════════════════════════════════════════════════════════

    /** 直接入参安全取值（nullable） */
    inline fun <reified T> input(): T? = directInput as? T

    /** 直接入参必传，缺失抛异常 */
    inline fun <reified T> requireInput(): T =
        input<T>() ?: throw InvalidParamException("directInput is required")

    /** 直接入参带默认值 */
    inline fun <reified T> input(default: T): T = input<T>() ?: default

    /** 直接入参自定义转换 */
    inline fun <reified T> input(mapper: (Any) -> T): T? = directInput?.let(mapper)

    /** 直接入参自定义转换 + 默认值 */
    inline fun <reified T> input(default: T, mapper: (Any) -> T): T =
        input(mapper) ?: default

    fun inputAsString(default: String = ""): String = input(default) { it.toString() }
    fun inputAsInt(default: Int = 0): Int = input(default, ::convertToInt)
    fun inputAsLong(default: Long = 0L): Long = input(default, ::convertToLong)
    fun inputAsBoolean(default: Boolean = false): Boolean = input(default, ::convertToBoolean)

    // ═══════════════════════════════════════════════════════════════
    // Schema 感知的 directInput 字段访问（动态数据访问策略）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 解析后的 [SchemaFormat]，当 [schemaFormat] 为 null 时返回 null。
     */
    val resolvedSchemaFormat: SchemaFormat?
        get() = schemaFormat?.let { SchemaFormat.fromCode(it) }

    /**
     * directInput 的 Map 视图（Schema 格式感知）。
     *
     * - 当设置了 [schemaFormat] + [providerRegistry] 时，返回 [SchemaBackedMap]，
     *   其 `get()` / `containsKey()` 直接委托给对应的 [com.fluxion.schema.api.SchemaDataProvider]，
     *   无需先调用 `toMap()` 做全量转换。
     * - 否则回退到传统 `Map<String, Any?>` 强转。
     * - directInput 为 null 时返回空 Map。
     *
     * ```kotlin
     * // 像普通 Map 一样使用，底层自动走 SchemaDataProvider
     * val name = nodeInput.directInputView["name"]
     * val hasAge = nodeInput.directInputView.containsKey("age")
     * nodeInput.directInputView.forEach { (k, v) -> ... }
     * ```
     */
    val directInputView: Map<String, Any?> by lazy {
        val data = directInput ?: return@lazy emptyMap<String, Any?>()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            SchemaBackedMap(data, registry.getProvider(format))
        } else {
            @Suppress("UNCHECKED_CAST")
            (data as? Map<String, Any?>) ?: emptyMap()
        }
    }

    /**
     * 获取指定依赖节点输出的 Map 视图（Schema 格式感知）。
     *
     * 与 [directInputView] 同理，但作用于 declaredDeps 中的某个节点输出。
     * 依赖输出可能是 Map（JSON）或 DynamicMessage（Protobuf）或 GenericRecord（Avro），
     * 当配置了 Schema 信息时自动通过 provider 访问字段。
     *
     * @param nodeId 依赖节点 ID
     * @return Map 视图，不存在时返回空 Map
     */
    fun depView(nodeId: String): Map<String, Any?> {
        val data = declaredDeps[nodeId] ?: return emptyMap()
        val format = resolvedSchemaFormat
        val registry = providerRegistry
        if (format != null && registry != null) {
            return SchemaBackedMap(data, registry.getProvider(format))
        }
        @Suppress("UNCHECKED_CAST")
        return (data as? Map<String, Any?>) ?: emptyMap()
    }

    /**
     * 从 directInput 中按字段名获取值（Schema 格式感知）。
     *
     * 委托给 [directInputView]，当有 Schema 信息时自动走对应的 SchemaDataProvider，
     * 否则回退到传统 Map 访问。
     */
    fun inputField(field: String): Any? = directInputView[field]

    /**
     * 从 directInput 中获取必填字段值，缺失时抛异常。
     */
    fun requireInputField(field: String): Any =
        inputField(field) ?: throw InvalidParamException("directInput.$field is required")

    /**
     * 从 directInput 中获取字段值，带默认值。
     */
    fun inputFieldOrDefault(field: String, default: Any? = null): Any? =
        inputField(field) ?: default

    /**
     * 判断 directInput 中是否存在指定字段（Schema 格式感知）。
     */
    fun hasInputField(field: String): Boolean = directInputView.containsKey(field)

    /**
     * 将 directInput 转换为通用 Map 表示（Schema 格式感知）。
     *
     * 委托给 [directInputView]。
     */
    fun inputAsMap(): Map<String, Any?> = directInputView

    /**
     * 获取 directInput 中所有可用字段名（Schema 格式感知）。
     */
    fun inputFieldNames(): Set<String> = directInputView.keys

    // ═══════════════════════════════════════════════════════════════
    // 节点配置参数（nodeParams）安全访问 — 覆盖 90% 调用方
    // ═══════════════════════════════════════════════════════════════

    /** 节点参数安全取值（nullable） */
    inline fun <reified T> param(key: String): T? = nodeParams[key] as? T

    /** 节点参数必传，缺失抛异常 */
    inline fun <reified T> requireParam(key: String): T =
        param<T>(key) ?: throw InvalidParamException("nodeParams.$key is required")

    /** 节点参数带默认值 */
    inline fun <reified T> param(key: String, default: T): T = param<T>(key) ?: default

    /** 节点参数自定义转换 */
    inline fun <reified T> param(key: String, mapper: (Any) -> T): T? =
        nodeParams[key]?.let(mapper)

    /** 节点参数自定义转换 + 默认值 */
    inline fun <reified T> param(key: String, default: T, mapper: (Any) -> T): T =
        param(key, mapper) ?: default

    fun paramAsString(key: String, default: String = ""): String =
        param(key, default) { it.toString() }

    fun paramAsInt(key: String, default: Int = 0): Int =
        param(key, default, ::convertToInt)

    fun paramAsLong(key: String, default: Long = 0L): Long =
        param(key, default, ::convertToLong)

    fun paramAsBoolean(key: String, default: Boolean = false): Boolean =
        param(key, default, ::convertToBoolean)

    // ═══════════════════════════════════════════════════════════════
    // 工作流原始入参（workflowInput）安全访问
    // ═══════════════════════════════════════════════════════════════

    /** 工作流原始入参安全取值（nullable） */
    inline fun <reified T> wfInput(key: String): T? = workflowInput[key] as? T

    /** 工作流原始入参必传，缺失抛异常 */
    inline fun <reified T> requireWfInput(key: String): T =
        wfInput<T>(key) ?: throw InvalidParamException("workflowInput.$key is required")

    /** 工作流原始入参带默认值 */
    inline fun <reified T> wfInput(key: String, default: T): T = wfInput<T>(key) ?: default

    /** 工作流原始入参自定义转换 */
    inline fun <reified T> wfInput(key: String, mapper: (Any) -> T): T? =
        workflowInput[key]?.let(mapper)

    /** 工作流原始入参自定义转换 + 默认值 */
    inline fun <reified T> wfInput(key: String, default: T, mapper: (Any) -> T): T =
        wfInput(key, mapper) ?: default

    /** 工作流入参转 String */
    fun wfInputAsString(key: String, default: String = ""): String =
        wfInput(key, default) { it.toString() }

    /** 工作流入参转 Int */
    fun wfInputAsInt(key: String, default: Int = 0): Int =
        wfInput(key, default, ::convertToInt)

    /** 工作流入参转 Long */
    fun wfInputAsLong(key: String, default: Long = 0L): Long =
        wfInput(key, default, ::convertToLong)

    /** 工作流入参转 Boolean */
    fun wfInputAsBoolean(key: String, default: Boolean = false): Boolean =
        wfInput(key, default, ::convertToBoolean)

    // ═══════════════════════════════════════════════════════════════
    // 声明依赖（declaredDeps）安全访问
    // ═══════════════════════════════════════════════════════════════

    /** 声明依赖输出安全取值（nullable） */
    inline fun <reified T> dep(nodeId: String): T? = declaredDeps[nodeId] as? T

    /** 声明依赖输出必传，缺失抛异常 */
    inline fun <reified T> requireDep(nodeId: String): T =
        dep<T>(nodeId) ?: throw InvalidParamException("declaredDeps.$nodeId is required")

    /** 声明依赖输出带默认值 */
    inline fun <reified T> dep(nodeId: String, default: T): T = dep<T>(nodeId) ?: default

    /** 声明依赖输出自定义转换 */
    inline fun <reified T> dep(nodeId: String, mapper: (Any) -> T): T? =
        declaredDeps[nodeId]?.let(mapper)

    /** 声明依赖输出自定义转换 + 默认值 */
    inline fun <reified T> dep(nodeId: String, default: T, mapper: (Any) -> T): T =
        dep(nodeId, mapper) ?: default

    /** 声明依赖输出转 String */
    fun depAsString(nodeId: String, default: String = ""): String =
        dep(nodeId, default) { it.toString() }

    /** 声明依赖输出转 Int */
    fun depAsInt(nodeId: String, default: Int = 0): Int =
        dep(nodeId, default, ::convertToInt)

    /** 声明依赖输出转 Long */
    fun depAsLong(nodeId: String, default: Long = 0L): Long =
        dep(nodeId, default, ::convertToLong)

    /** 声明依赖输出转 Boolean */
    fun depAsBoolean(nodeId: String, default: Boolean = false): Boolean =
        dep(nodeId, default, ::convertToBoolean)

    // ═══════════════════════════════════════════════════════════════
    // Schema 感知的 declaredDeps 字段访问
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从指定依赖节点的输出中按字段名获取值（Schema 格式感知）。
     *
     * 委托给 [depView]，自动通过对应的 SchemaDataProvider 访问。
     */
    fun depField(nodeId: String, field: String): Any? = depView(nodeId)[field]

    /**
     * 获取指定依赖节点输出中所有可用字段名（Schema 格式感知）。
     */
    fun depFieldNames(nodeId: String): Set<String> = depView(nodeId).keys

    /**
     * 从指定依赖节点的输出中获取必填字段值，缺失时抛异常。
     */
    fun requireDepField(nodeId: String, field: String): Any =
        depField(nodeId, field) ?: throw InvalidParamException("declaredDeps.$nodeId.$field is required")

    // ═══════════════════════════════════════════════════════════════
    // 结构化参数绑定（Structured Binding）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 解析参数值，支持字面量与结构化绑定两种模式。
     *
     * 所有字符串（包括结构化绑定的 prefix/suffix）均支持 `${variable}` 模板插值：
     * ```json
     * "user:${username}:${password}"                        // 纯字符串模板
     * { "$ref": "input.userId" }                             // 引用工作流入参
     * { "$ref": "n2.username" }                              // 引用上游节点输出
     * { "$ref": "n2.username", "prefix": "user:" }          // 带前缀拼接
     * { "$ref": "n2.username", "prefix": "user:${env}:" }   // prefix 也支持插值
     * ```
     *
     * `$ref` 格式：
     * - `input.{field}` → 从 workflowInput 取值
     * - `{nodeId}.{field}` → 从 declaredDeps[nodeId] 取子字段
     * - 裸字段名 → 依次查找 workflowInput → directInput → nodeParams
     */
    fun resolveBinding(value: Any?): String? {
        if (value == null) return null

        // ── 1. 字符串：解析 ${var} 模板插值 ──
        if (value is String) return interpolateTemplate(value)

        // ── 2. 结构化绑定对象 ──
        if (value is Map<*, *>) {
            val ref = value["\$ref"]?.toString()
                ?: return null  // 无 $ref 字段，非绑定对象

            val resolved: Any? = when {
                // input.{field} → workflowInput
                ref.startsWith("input.") -> {
                    val field = ref.removePrefix("input.")
                    workflowInput[field]
                }
                // {nodeId}.{field} → declaredDeps
                ref.contains('.') -> {
                    val dot = ref.indexOf('.')
                    val nodeId = ref.substring(0, dot)
                    val field = ref.substring(dot + 1)
                    depView(nodeId)[field]
                }
                // 裸字段名 → 依次查找 workflowInput → directInput → nodeParams
                else -> lookupVariable(ref)
            }

            val resolvedStr = resolved?.toString() ?: return null
            // prefix/suffix 也支持 ${var} 模板插值
            val prefix = interpolateTemplate(value["prefix"]?.toString() ?: "")
            val suffix = interpolateTemplate(value["suffix"]?.toString() ?: "")
            return "$prefix$resolvedStr$suffix"
        }

        // ── 3. 其他类型：toString 兜底 ──
        return value.toString()
    }

    /**
     * 解析字符串中的 `${variable}` 模板占位符。
     *
     * 变量查找顺序：workflowInput → directInput (Map) → nodeParams → declaredDeps (扁平展开)。
     * 未解析的占位符保持原样。
     */
    private fun interpolateTemplate(template: String): String {
        if (!template.contains("\${")) return template
        return DOLLAR_BRACE_PATTERN.replace(template) { match ->
            val varName = match.groupValues[1]
            lookupVariable(varName)?.toString() ?: match.value
        }
    }

    /** 按变量名依次查找 workflowInput → directInput → nodeParams → declaredDeps */
    private fun lookupVariable(name: String): Any? {
        workflowInput[name]?.let { return it }
        directInputView[name]?.let { return it }
        nodeParams[name]?.let { return it }
        // declaredDeps 扁平查找：支持 "nodeId.field" 格式
        if (name.contains('.')) {
            val dot = name.indexOf('.')
            val nodeId = name.substring(0, dot)
            val field = name.substring(dot + 1)
            depView(nodeId)[field]?.let { return it }
        }
        declaredDeps[name]?.let { return it }
        return null
    }

    /**
     * 批量解析参数列表，每个元素可以是字面量或绑定对象。
     */
    fun resolveBindingList(values: List<*>): List<String> =
        values.map { resolveBinding(it) ?: "" }

    // ═══════════════════════════════════════════════════════════════
    // 不可变更新（替代 Java record 的 withXxx）
    // ═══════════════════════════════════════════════════════════════

    /** 替换 directInput，其他字段保持不变 */
    fun withDirectInput(newDirectInput: Any?): NodeInput = copy(directInput = newDirectInput)

    /** 替换 nodeParams，其他字段保持不变 */
    fun withNodeParams(newNodeParams: Map<String, Any>?): NodeInput =
        copy(nodeParams = newNodeParams?.toMap() ?: emptyMap())

    /** 替换 workflowInput，其他字段保持不变 */
    fun withWorkflowInput(newWorkflowInput: Map<String, Any>?): NodeInput =
        copy(workflowInput = newWorkflowInput?.toMap() ?: emptyMap())

    /** 替换 declaredDeps，其他字段保持不变 */
    fun withDeclaredDeps(newDeclaredDeps: Map<String, Any>?): NodeInput =
        copy(declaredDeps = newDeclaredDeps?.toMap() ?: emptyMap())

    /** 设置 Schema 元信息（格式 + 名称 + 版本），其他字段保持不变 */
    fun withSchema(
        format: String,
        name: String? = null,
        version: Long? = null
    ): NodeInput = copy(schemaFormat = format, schemaName = name, schemaVersion = version)

    /** 设置 Schema 数据访问提供者注册表，其他字段保持不变 */
    fun withProviderRegistry(registry: SchemaDataProviderRegistry): NodeInput =
        copy(providerRegistry = registry)
}

/** 任意类型 → Int 的安全转换 */
private fun convertToInt(value: Any): Int = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> throw InvalidParamException("cannot convert to Int: $value")
}

/** 任意类型 → Long 的安全转换 */
private fun convertToLong(value: Any): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    is String -> value.toLong()
    else -> throw InvalidParamException("cannot convert to Long: $value")
}

/** 任意类型 → Boolean 的安全转换 */
private fun convertToBoolean(value: Any): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.lowercase().let {
        when (it) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> throw InvalidParamException("cannot convert to Boolean: $value")
        }
    }
    else -> throw InvalidParamException("cannot convert to Boolean: $value")
}
