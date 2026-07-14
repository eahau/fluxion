/**
 * Produces a uniform [SchemaField] tree from a compiled Protobuf message
 * [Descriptors.Descriptor].
 *
 * Covers:
 * * scalar-type mapping (int32→INTEGER, string→STRING, …)
 * * recursive nested MESSAGE extraction with cycle protection via `visited`
 * * `repeated` fields and `oneof` membership propagated via metadata
 * * field numbers exposed as `fieldNumber` metadata for downstream tooling.
 */
package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.google.protobuf.Descriptors

/**
 * Field extractor for the PROTOBUF schema format.
 *
 * Empty schemas (parsed value is the sentinel) return an empty list so
 * "any-value" callers never fail on a missing descriptor cast.
 */
class ProtobufSchemaFieldExtractor : SchemaFieldExtractor {

    override fun extractFields(schema: Schema): List<SchemaField> {
        if (schema.isEmpty) return emptyList()

        val descriptor = schema.parsed as? Descriptors.Descriptor
            ?: throw IllegalArgumentException("Schema parsed object is not a Protobuf Descriptor")

        return extractMessageFields(descriptor, mutableSetOf())
    }

    /**
     * Recursively extract fields from a message descriptor.
     *
     * @param visited set of fully-qualified message names already on the
     *                current recursion stack; used to break cyclic message
     *                references (e.g. `message Tree { Tree parent = 1; }`).
     */
    private fun extractMessageFields(
        descriptor: Descriptors.Descriptor,
        visited: MutableSet<String>
    ): List<SchemaField> {
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
                required = false,
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
