/**
 * Constructs [ExternalFunctionConfig] instances from an operator-authored
 * `Map<String, Any>` that may have been deserialised from JSON/YAML or
 * produced by the admin console's form binder.
 *
 * Two pragmatic design choices keep this parser usable across evolution:
 * 1. **Backward compatible flattening** – legacy declarations nested the
 *    real config under an `externalService` key. We flatten that map on top
 *    of the outer config using `putIfAbsent`, so both shapes parse to the
 *    same canonical instance and new deployments never see the nested form.
 * 2. **Known-but-opaque keys → extras** – `version`, `group`, and
 *    `parameterTypes` are meaningful for Dubbo and common enough to merit
 *    first-class promotion into [ExternalFunctionConfig.extras] when the
 *    operator wrote them at the top level. Keeping them in `extras` rather
 *    than on the data class avoids tying the shared model to one RPC stack.
 */
package com.fluxion.core.function.external

import com.fluxion.core.util.JsonUtil

/**
 * Stateless parser converting free-form maps to strongly typed
 * [ExternalFunctionConfig] records.
 */
object ExternalFunctionConfigParser {

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun parse(config: Map<String, Any>?): ExternalFunctionConfig {
        if (config.isNullOrEmpty()) return ExternalFunctionConfig()

        val effectiveConfig = flattenExternalService(config)

        val extras = (effectiveConfig["extras"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
        // Dubbo and gRPC specific knobs are often authored at top level in
        // simple forms; promote them to extras so the shared data class does
        // not grow RPC-specific fields.
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
        // putIfAbsent so top-level keys always win over the legacy nested block.
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
