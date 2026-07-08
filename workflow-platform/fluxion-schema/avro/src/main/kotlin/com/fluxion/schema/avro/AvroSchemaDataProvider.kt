/**
 * Avro-backed [SchemaDataProvider] that enables transparent field access on
 * Avro [GenericRecord] payloads through the generic `inputField("name")`
 * workflow API.
 *
 * Also handles `Map<String, *>` fallbacks (e.g. deserialised from JSON before
 * Avro processing) so mixed workloads behave consistently. Nested
 * GenericRecords, arrays, maps and `Utf8` values are recursively converted
 * to plain Kotlin collections when [toMap] is called.
 */
package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaDataProvider
import org.apache.avro.generic.GenericRecord
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Exposes Avro record fields through the generic data-access SPI.
 *
 * ### Input shapes supported
 * * [GenericRecord] — schema-aware field lookup; missing fields log a debug
 *   line and return `null`.
 * * `Map<*, *>` — standard indexed access.
 * * Anything else — [getField] returns `null` so callers can provide defaults.
 */
class AvroSchemaDataProvider : SchemaDataProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getField(data: Any, field: String): Any? {
        return when (data) {
            is GenericRecord -> {
                val schema = data.schema
                if (schema.getField(field) != null) {
                    data.get(field)
                } else {
                    // Logged at DEBUG rather than WARN because absent fields
                    // can be a valid optional-value case for union schemas.
                    log.debug { "Avro field [$field] not found in schema [${schema.fullName}]" }
                    null
                }
            }
            is Map<*, *> -> data[field]
            else -> null
        }
    }

    override fun hasField(data: Any, field: String): Boolean {
        return when (data) {
            is GenericRecord -> data.schema.getField(field) != null
            is Map<*, *> -> data.containsKey(field)
            else -> false
        }
    }

    override fun toMap(data: Any): Map<String, Any?> {
        return when (data) {
            is GenericRecord -> genericRecordToMap(data)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                data as Map<String, Any?>
            }
            else -> emptyMap()
        }
    }

    override fun getFieldNames(data: Any): Set<String> {
        return when (data) {
            is GenericRecord -> data.schema.fields.map { it.name() }.toSet()
            is Map<*, *> -> data.keys.filterIsInstance<String>().toSet()
            else -> emptySet()
        }
    }

    /**
     * Recursively flatten a GenericRecord to a Kotlin LinkedHashMap (preserving
     * field declaration order) while translating Avro-specific value types
     * into standard JVM equivalents.
     */
    private fun genericRecordToMap(record: GenericRecord): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (field in record.schema.fields) {
            result[field.name()] = convertAvroValue(record.get(field.pos()))
        }
        return result
    }

    private fun convertAvroValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is GenericRecord -> genericRecordToMap(value)
            is org.apache.avro.util.Utf8 -> value.toString()
            is List<*> -> value.map { convertAvroValue(it) }
            is Map<*, *> -> {
                val converted = linkedMapOf<String, Any?>()
                for ((k, v) in value) {
                    converted[k.toString()] = convertAvroValue(v)
                }
                converted
            }
            is java.nio.ByteBuffer -> {
                val bytes = ByteArray(value.remaining())
                value.duplicate().get(bytes)
                bytes
            }
            else -> value
        }
    }
}
