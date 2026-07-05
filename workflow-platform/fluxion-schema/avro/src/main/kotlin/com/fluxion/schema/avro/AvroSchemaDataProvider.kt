package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaDataProvider
import org.apache.avro.generic.GenericRecord
import org.slf4j.LoggerFactory

/**
 * Avro 数据访问提供者。
 *
 * 支持两种数据对象类型：
 * - [GenericRecord]：通过 `get(String key)` 访问字段
 * - `Map<String, Any?>`：作为 fallback，使用标准 Map 访问
 *
 * ### 使用场景
 *
 * 当工作流入参经过 Avro 协议解码后产生 `GenericRecord` 对象时，
 * 此 provider 使得 NodeInput 能够透明地通过 `inputField("fieldName")` 访问字段，
 * 无需调用方感知底层数据类型。
 *
 * ```kotlin
 * // 在 WorkflowFunction 中
 * val name = input.inputField("name")  // 自动使用 GenericRecord.get()
 * ```
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
                    log.debug("Avro field [{}] not found in schema [{}]", field, schema.fullName)
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
     * 将 GenericRecord 递归转换为 Map。
     *
     * - 嵌套 GenericRecord → 递归转为 Map
     * - Avro Array → 转为 List
     * - Avro Map → 转为 Map
     * - Utf8 → 转为 String
     * - 基本类型 → 直接使用 Java 值
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
