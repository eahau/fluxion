package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField

/**
 * Avro Schema 字段提取器。
 *
 * 将 Avro Schema（record / enum / array / map 等）转换为统一的 [SchemaField] 树，
 * 供前端字段选择器、参数面板等场景使用。
 */
class AvroSchemaFieldExtractor : SchemaFieldExtractor {

    override fun extractFields(schema: Schema): List<SchemaField> {
        val avroSchema = schema.parsed as? org.apache.avro.Schema
            ?: throw IllegalArgumentException("Schema parsed object is not an Avro Schema")
        return extractFromSchema(avroSchema)
    }

    private fun extractFromSchema(avroSchema: org.apache.avro.Schema): List<SchemaField> {
        return when (avroSchema.type) {
            org.apache.avro.Schema.Type.RECORD -> {
                avroSchema.fields.map { field ->
                    extractField(field)
                }
            }
            else -> emptyList()
        }
    }

    private fun extractField(field: org.apache.avro.Schema.Field): SchemaField {
        val fieldType = resolveType(field.schema())
        val nested = when (fieldType) {
            FieldType.RECORD, FieldType.OBJECT -> {
                extractFromSchema(unwrapUnion(field.schema()))
            }
            FieldType.ARRAY -> {
                val items = field.schema().let { s ->
                    if (s.type == org.apache.avro.Schema.Type.ARRAY) s.elementType
                    else unwrapUnion(s).let { if (it.type == org.apache.avro.Schema.Type.ARRAY) it.elementType else null }
                }
                items?.let { extractFromSchema(it) } ?: emptyList()
            }
            else -> {
                emptyList()
            }
        }

        return SchemaField(
            name = field.name(),
            type = fieldType,
            required = !isNullable(field.schema()),
            description = field.doc(),
            nestedFields = nested,
            metadata = buildMetadata(field)
        )
    }

    private fun resolveType(schema: org.apache.avro.Schema): FieldType {
        val effective = unwrapUnion(schema)
        return when (effective.type) {
            org.apache.avro.Schema.Type.STRING  -> FieldType.STRING
            org.apache.avro.Schema.Type.INT     -> FieldType.INTEGER
            org.apache.avro.Schema.Type.LONG    -> FieldType.LONG
            org.apache.avro.Schema.Type.FLOAT,
            org.apache.avro.Schema.Type.DOUBLE  -> FieldType.DOUBLE
            org.apache.avro.Schema.Type.BOOLEAN -> FieldType.BOOLEAN
            org.apache.avro.Schema.Type.NULL    -> FieldType.NULL
            org.apache.avro.Schema.Type.RECORD  -> FieldType.RECORD
            org.apache.avro.Schema.Type.ARRAY   -> FieldType.ARRAY
            org.apache.avro.Schema.Type.MAP     -> FieldType.OBJECT
            org.apache.avro.Schema.Type.ENUM    -> FieldType.STRING
            org.apache.avro.Schema.Type.BYTES,
            org.apache.avro.Schema.Type.FIXED   -> FieldType.ANY
            else -> FieldType.ANY
        }
    }

    /**
     * 解包 union 类型：如 ["null", "string"] → string schema。
     */
    private fun unwrapUnion(schema: org.apache.avro.Schema): org.apache.avro.Schema {
        return if (schema.type == org.apache.avro.Schema.Type.UNION) {
            schema.types.firstOrNull { it.type != org.apache.avro.Schema.Type.NULL } ?: schema
        } else {
            schema
        }
    }

    /**
     * 判断是否为可空字段（union 中包含 null 类型）。
     */
    private fun isNullable(schema: org.apache.avro.Schema): Boolean {
        return if (schema.type == org.apache.avro.Schema.Type.UNION) {
            schema.types.any { it.type == org.apache.avro.Schema.Type.NULL }
        } else {
            false
        }
    }

    private fun buildMetadata(field: org.apache.avro.Schema.Field): Map<String, Any> {
        val meta = mutableMapOf<String, Any>()
        val effective = unwrapUnion(field.schema())
        if (effective.type == org.apache.avro.Schema.Type.ENUM) {
            meta["enum"] = effective.enumSymbols
        }
        if (effective.type == org.apache.avro.Schema.Type.MAP) {
            meta["valueType"] = resolveType(effective.valueType).name
        }
        // 保留 Avro 原始 prop 属性
        field.getObjectProps()?.forEach { (k, v) ->
            if (k !in setOf("name", "type", "doc", "default")) meta[k] = v
        }
        return meta
    }
}
