/**
 * Built-in [SchemaDataProvider] that operates on generic `Map<String, *>`
 * payloads produced by JSON deserialization.
 *
 * Always available — requires zero additional dependencies beyond Jackson —
 * and serves as the default provider when no format-specific provider has
 * been registered or when the workflow payload is untyped JSON.
 */
package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaDataProvider

/**
 * Generic map-backed data access for JSON payloads.
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
