package com.fluxion.schema.avro

import org.apache.avro.Schema as AvroSchema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8

/**
 * Avro 动态数据转换器。
 *
 * 将普通 Java/Kotlin 数据结构（Map / List / 基本类型）转换为 Avro [GenericRecord]，
 * 无需代码生成，完全动态解释 Schema。
 *
 * 核心难点：Avro union 类型（如 `["null", "string"]`）需要显式指定分支类型，
 * 本转换器根据值的实际类型自动推断正确的 union 分支。
 */
internal object AvroDataConverter {

    /**
     * 将任意数据转换为 GenericRecord。
     *
     * @param data   Map 或 POJO（POJO 先通过 Jackson 转为 Map）
     * @param schema Avro Schema（必须是 record 类型）
     */
    fun toGenericRecord(data: Any?, schema: AvroSchema): GenericRecord {
        val effectiveSchema = unwrapUnion(schema)
        require(effectiveSchema.type == AvroSchema.Type.RECORD) {
            "Expected record schema, got ${effectiveSchema.type}"
        }

        val map = when (data) {
            is GenericRecord -> return data
            is Map<*, *> -> data
            null -> emptyMap<String, Any?>()
            else -> {
                val json = com.fluxion.schema.util.JsonUtil.serialize(data)
                com.fluxion.schema.util.JsonUtil.deserialize(json, Map::class.java)
            }
        }

        val record = GenericData.Record(effectiveSchema)
        for (field in effectiveSchema.fields) {
            val value = map[field.name()]
            record.put(field.name(), convertValue(value, field.schema()))
        }
        return record
    }

    /**
     * 递归转换值，按 Schema 类型匹配。
     */
    private fun convertValue(value: Any?, schema: AvroSchema): Any? {
        if (value == null) {
            return if (isNullable(schema)) null
            else throw IllegalArgumentException("Non-nullable field got null value for schema: $schema")
        }

        return when (schema.type) {
            AvroSchema.Type.UNION -> convertUnion(value, schema)
            AvroSchema.Type.RECORD -> toGenericRecord(value, schema)
            AvroSchema.Type.ARRAY -> convertArray(value, schema)
            AvroSchema.Type.MAP -> convertMap(value, schema)
            AvroSchema.Type.STRING -> when (value) {
                is String -> Utf8(value)
                is Utf8 -> value
                else -> Utf8(value.toString())
            }
            AvroSchema.Type.INT -> when (value) {
                is Int -> value
                is Number -> value.toInt()
                else -> value.toString().toInt()
            }
            AvroSchema.Type.LONG -> when (value) {
                is Long -> value
                is Number -> value.toLong()
                else -> value.toString().toLong()
            }
            AvroSchema.Type.FLOAT -> when (value) {
                is Float -> value
                is Number -> value.toFloat()
                else -> value.toString().toFloat()
            }
            AvroSchema.Type.DOUBLE -> when (value) {
                is Double -> value
                is Number -> value.toDouble()
                else -> value.toString().toDouble()
            }
            AvroSchema.Type.BOOLEAN -> when (value) {
                is Boolean -> value
                else -> value.toString().toBoolean()
            }
            AvroSchema.Type.BYTES -> when (value) {
                is ByteArray -> java.nio.ByteBuffer.wrap(value)
                else -> java.nio.ByteBuffer.wrap(value.toString().toByteArray())
            }
            AvroSchema.Type.ENUM -> {
                val enumSchema = schema
                GenericData.EnumSymbol(enumSchema, value.toString())
            }
            AvroSchema.Type.FIXED -> {
                val fixedSchema = schema
                val bytes = when (value) {
                    is ByteArray -> value
                    else -> value.toString().toByteArray()
                }
                GenericData.Fixed(fixedSchema, bytes)
            }
            else -> value
        }
    }

    /**
     * 处理 union 类型：根据值的实际类型选择匹配的分支。
     */
    private fun convertUnion(value: Any?, schema: AvroSchema): Any? {
        if (value == null) return null

        val branches = schema.types
        // 优先匹配非 null 分支
        for (branch in branches) {
            if (branch.type == AvroSchema.Type.NULL) continue
            if (isCompatible(value, branch)) {
                return convertValue(value, branch)
            }
        }
        // fallback：使用第一个非 null 分支
        val fallback = branches.firstOrNull { it.type != AvroSchema.Type.NULL }
            ?: throw IllegalArgumentException("No compatible union branch for value: $value")
        return convertValue(value, fallback)
    }

    /**
     * 判断值是否与指定 Schema 类型兼容。
     */
    private fun isCompatible(value: Any, schema: AvroSchema): Boolean {
        return when (schema.type) {
            AvroSchema.Type.STRING -> value is String
            AvroSchema.Type.INT -> value is Int
            AvroSchema.Type.LONG -> value is Long || value is Int
            AvroSchema.Type.FLOAT -> value is Float || value is Int
            AvroSchema.Type.DOUBLE -> value is Double || value is Float || value is Int || value is Long
            AvroSchema.Type.BOOLEAN -> value is Boolean
            AvroSchema.Type.RECORD -> value is Map<*, *> || value is GenericRecord
            AvroSchema.Type.ARRAY -> value is List<*> || value is Array<*>
            AvroSchema.Type.MAP -> value is Map<*, *>
            AvroSchema.Type.ENUM -> value is String
            else -> true
        }
    }

    private fun convertArray(value: Any, schema: AvroSchema): Any {
        val items = when (value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> throw IllegalArgumentException("Expected array/list, got ${value::class.java.name}")
        }
        return items.map { convertValue(it, schema.elementType) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertMap(value: Any, schema: AvroSchema): Map<Utf8, Any?> {
        val map = value as? Map<*, *>
            ?: throw IllegalArgumentException("Expected map, got ${value::class.java.name}")
        return map.entries.associate { (k, v) ->
            Utf8(k.toString()) to convertValue(v, schema.valueType)
        }
    }

    private fun unwrapUnion(schema: AvroSchema): AvroSchema {
        return if (schema.type == AvroSchema.Type.UNION) {
            schema.types.firstOrNull { it.type != AvroSchema.Type.NULL } ?: schema
        } else {
            schema
        }
    }

    private fun isNullable(schema: AvroSchema): Boolean {
        return if (schema.type == AvroSchema.Type.UNION) {
            schema.types.any { it.type == AvroSchema.Type.NULL }
        } else {
            false
        }
    }
}
