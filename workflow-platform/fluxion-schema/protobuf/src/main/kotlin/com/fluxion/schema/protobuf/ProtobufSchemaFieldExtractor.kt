package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.google.protobuf.Descriptors

/**
 * Protobuf Schema 字段提取器。
 *
 * 从 [Descriptors.Descriptor] 中提取 message 字段定义，
 * 转换为统一的 [SchemaField] 树供前端使用。
 *
 * 支持：
 * - 基本类型映射（int32/int64/string/bool 等）
 * - 嵌套 message 递归提取
 * - repeated 字段标记
 * - oneof 字段标记
 * - 字段编号（field number）
 */
class ProtobufSchemaFieldExtractor : SchemaFieldExtractor {

    override fun extractFields(schema: Schema): List<SchemaField> {
        if (schema.isEmpty) return emptyList()

        val descriptor = schema.parsed as? Descriptors.Descriptor
            ?: throw IllegalArgumentException("Schema parsed object is not a Protobuf Descriptor")

        return extractMessageFields(descriptor, mutableSetOf())
    }

    /**
     * 从 Descriptor 提取字段列表。
     *
     * @param visited 已访问的 message 全名集合，用于循环引用检测
     */
    private fun extractMessageFields(
        descriptor: Descriptors.Descriptor,
        visited: MutableSet<String>
    ): List<SchemaField> {
        // 循环引用检测
        if (descriptor.fullName in visited) return emptyList()
        visited.add(descriptor.fullName)

        return descriptor.fields.map { field ->
            val fieldType = mapProtobufType(field.type)
            val nested = if (fieldType == FieldType.MESSAGE && field.messageType != null) {
                extractMessageFields(field.messageType, visited)
            } else {
                emptyList()
            }

            SchemaField(
                name = field.name,
                type = fieldType,
                required = false,  // Protobuf 3 没有 required
                description = null,
                format = field.type.name,
                nestedFields = nested,
                metadata = buildMetadata(field)
            )
        }
    }

    private fun mapProtobufType(type: Descriptors.FieldDescriptor.Type): FieldType {
        return when (type) {
            Descriptors.FieldDescriptor.Type.STRING -> FieldType.STRING
            Descriptors.FieldDescriptor.Type.INT32,
            Descriptors.FieldDescriptor.Type.INT64,
            Descriptors.FieldDescriptor.Type.UINT32,
            Descriptors.FieldDescriptor.Type.UINT64,
            Descriptors.FieldDescriptor.Type.SINT32,
            Descriptors.FieldDescriptor.Type.SINT64,
            Descriptors.FieldDescriptor.Type.FIXED32,
            Descriptors.FieldDescriptor.Type.FIXED64,
            Descriptors.FieldDescriptor.Type.SFIXED32,
            Descriptors.FieldDescriptor.Type.SFIXED64 -> FieldType.INTEGER
            Descriptors.FieldDescriptor.Type.FLOAT,
            Descriptors.FieldDescriptor.Type.DOUBLE -> FieldType.DOUBLE
            Descriptors.FieldDescriptor.Type.BOOL -> FieldType.BOOLEAN
            Descriptors.FieldDescriptor.Type.BYTES -> FieldType.ANY
            Descriptors.FieldDescriptor.Type.ENUM -> FieldType.STRING
            Descriptors.FieldDescriptor.Type.MESSAGE -> FieldType.MESSAGE
            Descriptors.FieldDescriptor.Type.GROUP -> FieldType.MESSAGE
            else -> FieldType.ANY
        }
    }

    private fun buildMetadata(field: Descriptors.FieldDescriptor): Map<String, Any> {
        val meta = mutableMapOf<String, Any>()
        meta["fieldNumber"] = field.number
        meta["protobufType"] = field.type.name
        if (field.isRepeated) {
            meta["repeated"] = true
        }
        if (field.containingOneof != null) {
            meta["oneof"] = field.containingOneof.name
        }
        if (field.hasDefaultValue()) {
            meta["defaultValue"] = field.defaultValue.toString()
        }
        return meta
    }
}
