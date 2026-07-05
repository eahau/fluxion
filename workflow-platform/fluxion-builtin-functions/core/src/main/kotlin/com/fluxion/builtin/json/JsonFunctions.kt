package com.fluxion.builtin.json

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas

import com.fasterxml.jackson.core.type.TypeReference
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import org.slf4j.*

/**
 * 内置 JSON 序列化函数（builtin:toJson）
 */
class JsonSerializeFunction : WorkflowFunction<String>, BuiltinFunction {

    companion object {
        /** 静态工具方法：将任意对象序列化为 JSON 字符串 */
        @JvmStatic
        fun serialize(source: Any?): String = JsonUtil.serialize(source)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<String> {
        val source = input.directInput
        val pretty = input.paramAsBoolean("pretty", false)

        // 若已是 JSON 字符串，直接透传
        if (source is String) {
            val trimmed = source.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
                log.debug { "builtin:toJson: input is already JSON string, passthrough" }
                return FunctionResult.success(trimmed)
            }
        }

        return try {
            val json = if (pretty) JsonUtil.serializePretty(source) else JsonUtil.serialize(source)
            log.debug { "builtin:toJson serialized, pretty=$pretty, length=${json.length}" }
            FunctionResult.success(json)
        } catch (ex: Exception) {
            throw WorkflowNodeException("builtin:toJson", RuntimeException("JSON serialize failed: ${ex.message}", ex))
        }
    }

    override fun meta() = BuiltinFunctionMetas.TO_JSON
}

/**
 * 内置 JSON 解析函数（builtin:fromJson）
 */
class JsonParseFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val raw: Any? = input.directInput ?: input.param<Any>("json")
        if (raw == null) {
            throw WorkflowNodeException(meta().name,
                IllegalArgumentException("No JSON input: directInput is null and nodeParams.json is missing"))
        }

        // 若已是结构化对象，直接透传
        if (raw is Map<*, *> || raw is List<*>) return FunctionResult.success(raw)

        val jsonStr = raw.toString().trim()
        if (jsonStr.isEmpty()) {
            throw WorkflowNodeException(meta().name, IllegalArgumentException("JSON input is empty string"))
        }

        val type = input.param("type", "auto").lowercase()

        return try {
            val result: Any? = when (type) {
                "map" -> JsonUtil.toMap(jsonStr)
                "list" -> JsonUtil.toList(jsonStr)
                else -> JsonUtil.deserialize(jsonStr, Any::class.java)
            }
            log.debug { "builtin:fromJson parsed type=$type, result class=${result?.javaClass?.simpleName}" }
            FunctionResult.success(result)
        } catch (ex: Exception) {
            throw WorkflowNodeException(meta().name, RuntimeException("JSON parse failed: ${ex.message}", ex))
        }
    }

    override fun meta() = BuiltinFunctionMetas.FROM_JSON
}

/**
 * 内置 JSON 提取函数（builtin:jsonExtract）
 */
class JsonExtractFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val source = requireNotNull(input.directInput) { "directInput is required for JsonExtractFunction" }
        val mappings = input.param<Map<String, String>>("mappings")
        val lenient = input.paramAsBoolean("defaultOnMissing", false)

        if (mappings.isNullOrEmpty()) {
            return if (source is Map<*, *>) FunctionResult.success(source.uncheckedCast<Map<String, Any?>>()!!)
            else FunctionResult.success(mapOf("value" to source))
        }

        val json = try {
            if (source is String) source else JsonSerializeFunction.serialize(source)
        } catch (ex: Exception) {
            throw WorkflowNodeException(meta().name, ex)
        }

        val result = LinkedHashMap<String, Any?>()
        for ((targetKey, jsonPath) in mappings) {
            try {
                result[targetKey] = JsonPath.read(json, jsonPath)
            } catch (_: PathNotFoundException) {
                if (lenient) {
                    result[targetKey] = null
                    log.debug { "JsonPath [$jsonPath] not found in source, set to null" }
                } else {
                    throw WorkflowNodeException(meta().name, RuntimeException("JsonPath not found: $jsonPath"))
                }
            }
        }
        return FunctionResult.success(result)
    }

    override fun meta() = BuiltinFunctionMetas.JSON_EXTRACT
}

/**
 * 内置 JSON 转换函数（builtin:jsonTransform）
 */
class JsonTransformFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val expression = input.param<String>("expression")
        require(!expression.isNullOrBlank()) { "nodeParams.expression is required for builtin:jsonTransform" }

        val defaultValue = input.param<Any>("defaultValue")
        val data = input.directInput

        return try {
            val json = data as? String ?: JsonUtil.serialize(data)
            val result = JsonPath.read<Any?>(json, expression)
            FunctionResult.success(result)
        } catch (_: PathNotFoundException) {
            log.debug { "JsonPath not found: $expression, returning default" }
            FunctionResult.success(defaultValue)
        } catch (e: Exception) {
            log.warn { "JsonTransform failed: ${e.message}" }
            FunctionResult.success(defaultValue)
        }
    }

    override fun meta() = BuiltinFunctionMetas.JSON_TRANSFORM
}

/**
 * 内置类型转换函数（builtin:convert）
 *
 * 支持 Map / POJO / JSON 字符串之间的相互转换。
 *
 * 示例：
 *   - 将 POJO/JSON 字符串转为 Map：targetType=map
 *   - 将任意对象转为 JSON 字符串：targetType=json
 *   - 将 Map/JSON 字符串转为指定 POJO：targetType=pojo, targetClass=xxx.xxx.Xxx
 *   - 将 JSON 字符串/数组转为 List：targetType=list
 */
class ConvertFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val targetType = input.param("targetType", "map").lowercase()
        val pretty = input.paramAsBoolean("pretty", false)

        var source: Any? = input.directInput
            ?: input.param<Any>("source")
            ?: throw WorkflowNodeException(meta().name, IllegalArgumentException("directInput or nodeParams.source is required"))

        // 若源是 JSON 字符串且目标不是 json，先解析为结构化对象
        if (source is String && targetType != "json") {
            source = try {
                JsonUtil.deserialize(source, Any::class.java)
            } catch (ex: Exception) {
                throw WorkflowNodeException(meta().name, RuntimeException("Source is not valid JSON: ${ex.message}", ex))
            }
        }

        return try {
            val result: Any = when (targetType) {
                "json" -> if (pretty) JsonUtil.serializePretty(source) else JsonUtil.serialize(source)
                "map" -> JsonUtil.toMap(source as Any)
                "list" -> when (source) {
                    is String -> JsonUtil.toList(source)
                    is List<*> -> source
                    else -> JsonUtil.convertValue(source, object : TypeReference<List<Any>>() {})
                }
                "pojo" -> {
                    val className = input.param<String>("targetClass")
                        ?: throw WorkflowNodeException(meta().name, IllegalArgumentException("nodeParams.targetClass is required when targetType=pojo"))
                    val clazz = Class.forName(className)
                    JsonUtil.convertValue(source, clazz)
                }
                else -> throw WorkflowNodeException(meta().name, IllegalArgumentException("Unsupported targetType: $targetType"))
            }
            log.debug { "builtin:convert targetType=$targetType, result class=${result.javaClass.simpleName}" }
            FunctionResult.success(result)
        } catch (ex: WorkflowNodeException) {
            throw ex
        } catch (ex: Exception) {
            throw WorkflowNodeException(meta().name, RuntimeException("Convert failed: ${ex.message}", ex))
        }
    }

    override fun meta() = BuiltinFunctionMetas.CONVERT
}
