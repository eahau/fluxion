package com.fluxion.core.function.external

import com.fluxion.core.util.JsonUtil

/**
 * 将 Map 配置解析为 [ExternalFunctionConfig]。
 *
 * 协议专属字段（如 version、group、parameterTypes）统一进入 [ExternalFunctionConfig.extras]，
 * 以保持配置模型协议无关；解析器同时兼容旧配置中这些字段位于顶层的情况。
 */
object ExternalFunctionConfigParser {

    @JvmStatic
    fun parse(config: Map<String, Any>?): ExternalFunctionConfig {
        if (config.isNullOrEmpty()) return ExternalFunctionConfig()

        // 兼容旧版前端/旧数据：config.externalService 嵌套结构提升到顶层
        val effectiveConfig = flattenExternalService(config)

        val extras = (effectiveConfig["extras"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
        // 兼容旧配置：顶层协议专属字段下沉到 extras
        listOf("version", "group", "parameterTypes").forEach { key ->
            effectiveConfig[key]?.let { extras.putIfAbsent(key, it) }
        }

        return ExternalFunctionConfig(
            protocol = effectiveConfig["protocol"]?.toString() ?: "http",
            service = effectiveConfig["service"]?.toString(),
            method = effectiveConfig["method"]?.toString(),
            endpoint = effectiveConfig["endpoint"]?.toString(),
            timeoutMs = (effectiveConfig["timeoutMs"] as? Number)?.toLong() ?: 0,
            headers = effectiveConfig["headers"] as? Map<String, String>,
            paramMapping = parseParamMapping(effectiveConfig["paramMapping"]),
            extras = extras.ifEmpty { null }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenExternalService(config: Map<String, Any>): Map<String, Any> {
        val externalService = config["externalService"] as? Map<String, Any> ?: return config
        val merged = config.toMutableMap()
        merged.remove("externalService")
        externalService.forEach { (key, value) ->
            merged.putIfAbsent(key, value)
        }
        return merged
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseParamMapping(value: Any?): Map<String, String>? {
        if (value is Map<*, *>) return value as? Map<String, String>
        if (value is String) {
            return try {
                JsonUtil.toMap(value) as? Map<String, String>
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
