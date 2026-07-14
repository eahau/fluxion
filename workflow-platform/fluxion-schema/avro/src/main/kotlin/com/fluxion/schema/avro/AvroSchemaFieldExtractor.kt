/**
 * Produces a uniform [SchemaField] tree from a compiled Avro schema.
 *
 * Handles the constructs that appear in typical workflow payloads: RECORD
 * fields, ARRAY item types, MAP value types, ENUM symbols, and UNION null
 * wrappers (which determine whether a field is marked required or nullable).
 */
package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField

/**
 * Field extractor implementation for the AVRO format.
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
                // Peel off a potential union wrapper before reading the
                // element schema — `["null", {"type":"array", ...}]` is the
                // canonical Avro way to express "nullable list of T".
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
     * Unwrap nullable unions (`["null", "string"]`) to the non-null member
     * type, preserving the schema itself when it is not a union or is entirely
     * `null`.
     */
    private fun unwrapUnion(schema: org.apache.avro.Schema): org.apache.avro.Schema {
        return if (schema.type == org.apache.avro.Schema.Type.UNION) {
            schema.types.firstOrNull { it.type != org.apache.avro.Schema.Type.NULL } ?: schema
        } else {
            schema
        }
    }

    /**
     * Mark a field as nullable when its schema is (or wraps) a union that
     * includes the `null` type — Avro's idiomatic equivalent of optional.
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
        field.getObjectProps()?.forEach { (k, v) ->
            if (k !in setOf("name", "type", "doc", "default")) meta[k] = v
        }
        return meta
    }
}
