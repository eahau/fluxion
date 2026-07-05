package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaDataProvider

/**
 * JSON Schema 数据访问提供者（内置默认实现）。
 *
 * 处理 `Map<String, Any?>` 格式的 JSON 数据，
 * 提供标准的 Map 键值访问。始终可用，无需额外依赖。
 */
class JsonSchemaDataProvider : SchemaDataProvider {

    override fun getField(data: Any, field: String): Any? = when (data) {
        is Map<*, *> -> data[field]
        else -> null
    }

    override fun hasField(data: Any, field: String): Boolean = when (data) {
        is Map<*, *> -> data.containsKey(field)
        else -> false
    }

    @Suppress("UNCHECKED_CAST")
    override fun toMap(data: Any): Map<String, Any?> = when (data) {
        is Map<*, *> -> data as Map<String, Any?>
        else -> emptyMap()
    }

    override fun getFieldNames(data: Any): Set<String> = when (data) {
        is Map<*, *> -> data.keys.filterIsInstance<String>().toSet()
        else -> emptySet()
    }
}
