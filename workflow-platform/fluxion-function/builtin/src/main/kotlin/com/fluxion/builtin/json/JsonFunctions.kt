package com.fluxion.builtin.json

import com.fasterxml.jackson.core.type.TypeReference
import com.fluxion.builtin.BuiltinFunction
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory

/**
 * Built-in JSON serializer (`builtin:toJson`).
 *
 * Accepts any object and renders it to a JSON string. When the input is
 * already a JSON string (starts with `{`, `[`, or `"`) the raw value is
 * passed through unchanged — this short-circuits unnecessary parse-serialize
 * round-trips on functions that already emit JSON.
 */
class JsonSerializeFunction : WorkflowFunction<String>, BuiltinFunction {

    companion object {
        /** Utility helper — serializes any value to a compact JSON string. */
        @JvmStatic
        fun serialize(source: Any?): String = JsonUtil.serialize(source)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:toJson"

    override fun apply(input: NodeInput): FunctionResult<String> {
        val source = input.directInput
        val pretty = input.paramAsBoolean("pretty", false)

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
            throw WorkflowNodeException(functionName, RuntimeException("JSON serialize failed: ${ex.message}", ex))
        }
    }
}

/**
 * Built-in JSON parser (`builtin:fromJson`).
 *
 * Accepts a JSON string as either directInput or `nodeParams.json` and
 * materializes it into structured objects. By default Jackson's polymorphic
 * `Any` deserializer is used (maps → LinkedHashMap, arrays → ArrayList);
 * callers can opt into `targetType = map | list` for more control.
 *
 * Already-structured inputs (Map / List) pass through untouched.
 */
class JsonParseFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:fromJson"

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val raw: Any? = input.directInput ?: input.param<Any>("json")
        if (raw == null) {
            throw WorkflowNodeException(functionName,
                IllegalArgumentException("No JSON input: directInput is null and nodeParams.json is missing"))
        }

        if (raw is Map<*, *> || raw is List<*>) return FunctionResult.success(raw)

        val jsonStr = raw.toString().trim()
        if (jsonStr.isEmpty()) {
            throw WorkflowNodeException(functionName, IllegalArgumentException("JSON input is empty string"))
        }

        val type = input.param("type", "auto").lowercase()

        return try {
            val result: Any = when (type) {
                "map" -> JsonUtil.toMap(jsonStr)
                "list" -> JsonUtil.toList(jsonStr)
                else -> JsonUtil.deserialize(jsonStr, Any::class.java)
            }
            log.debug { "builtin:fromJson parsed type=$type, result class=${result?.javaClass?.simpleName}" }
            FunctionResult.success(result)
        } catch (ex: Exception) {
            throw WorkflowNodeException(functionName, RuntimeException("JSON parse failed: ${ex.message}", ex))
        }
    }
}

/**
 * Built-in JSON extraction function (`builtin:jsonExtract`).
 *
 * Applies a map of `{targetKey → JayWay JsonPath expression}` against the
 * input document and returns a Map of the extracted values.
 *
 * Behavior:
 * - If `mappings` is empty/null and the source is already a Map, that Map is
 *   returned as-is (convenience pass-through).
 * - `defaultOnMissing = true` swallows [PathNotFoundException] and writes
 *   `null` for missing paths; otherwise the node fails.
 */
class JsonExtractFunction : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:jsonExtract"

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
            throw WorkflowNodeException(functionName, ex)
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
                    throw WorkflowNodeException(functionName, RuntimeException("JsonPath not found: `$jsonPath"))
                }
            }
        }
        return FunctionResult.success(result)
    }
}

/**
 * Built-in single-expression JSON transform (`builtin:jsonTransform`).
 *
 * Evaluates ONE JayWay JsonPath expression against the input and returns
 * the scalar/list/map result. Missing-path and other JsonPath exceptions
 * return `defaultValue` (if set) instead of failing the node.
 */
class JsonTransformFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:jsonTransform"

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
}

/**
 * Built-in generic type converter (`builtin:convert`).
 *
 * Bridging function for common type-shapes that occur at workflow boundaries:
 * - `targetType = map`  — arbitrary object / JSON string → `Map<String, Any?>`
 * - `targetType = list` — JSON array string / iterable → `List<Any>`
 * - `targetType = json` — arbitrary value → serialized JSON string
 * - `targetType = pojo` — map/json → reflective POJO (`targetClass` required)
 *
 * Incoming JSON strings are automatically parsed before conversion; POJOs
 * go through Jackson's convertValue so setters / constructors don't need
 * explicit annotations.
 */
class ConvertFunction : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:convert"

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val targetType = input.param("targetType", "map").lowercase()
        val pretty = input.paramAsBoolean("pretty", false)

        var source: Any? = input.directInput
            ?: input.param<Any>("source")
            ?: throw WorkflowNodeException(functionName, IllegalArgumentException("directInput or nodeParams.source is required"))

        // If source is JSON text and target isn't plain "json", pre-parse so
        // downstream converters (toMap / toList / toPojo) see structured data.
        if (source is String && targetType != "json") {
            source = try {
                JsonUtil.deserialize(source, Any::class.java)
            } catch (ex: Exception) {
                throw WorkflowNodeException(functionName, RuntimeException("Source is not valid JSON: ${ex.message}", ex))
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
                        ?: throw WorkflowNodeException(functionName, IllegalArgumentException("nodeParams.targetClass is required when targetType=pojo"))
                    val clazz = Class.forName(className)
                    JsonUtil.convertValue(source, clazz)
                }
                else -> throw WorkflowNodeException(functionName, IllegalArgumentException("Unsupported targetType: `$targetType"))
            }
            log.debug { "builtin:convert targetType=$targetType, result class=${result.javaClass.simpleName}" }
            FunctionResult.success(result)
        } catch (ex: WorkflowNodeException) {
            throw ex
        } catch (ex: Exception) {
            throw WorkflowNodeException(functionName, RuntimeException("Convert failed: ${ex.message}", ex))
        }
    }
}
