package com.fluxion.schema.model

/**
 * Format-agnostic schema field descriptor — every format-specific extractor
 * (JSON Schema, Protobuf, Avro) reduces its native field tree into this
 * common shape so the admin UI, rule evaluator, and parameter-form UIs can
 * render a single, format-independent field tree.
 *
 * Nested fields live in [nestedFields]; there is no separate `SchemaFieldTree`
 * wrapper because the recursive structure of [SchemaField] itself already IS
 * the tree. Leaf nodes (primitives) carry an empty [nestedFields] list.
 *
 * @property name         field key in the parent object.
 * @property type         canonical logical type; maps to format-specific
 *                          wire types via per-format conventions (e.g. JSON
 *                          Schema `integer` → [FieldType.INTEGER], Protobuf
 *                          `int64` → [FieldType.LONG]).
 * @property required     true for required fields, false for optional / nullable.
 * @property description  human-readable doc string, forwarded verbatim from
 *                          the source schema (JSON Schema `description`,
 *                          Protobuf comments, Avro `doc`).
 * @property format       format-specific sub-type hint, e.g. `"email"`,
 *                          `"date-time"` in JSON Schema — purely advisory,
 *                          rendered by UIs but not validated generically.
 * @property nestedFields child fields for `OBJECT` / `ARRAY item` /
 *                          Protobuf `MESSAGE` / Avro `RECORD` nodes.
 * @property metadata     escape hatch for format-specific extras:
 *                          `enum` values, `pattern` regex, Protobuf field
 *                          numbers, Avro default values, etc. Keys follow
 *                          no cross-format contract.
 */
data class SchemaField(
    val name: String,
    val type: FieldType,
    val required: Boolean = false,
    val description: String? = null,
    val format: String? = null,
    val nestedFields: List<SchemaField> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)
