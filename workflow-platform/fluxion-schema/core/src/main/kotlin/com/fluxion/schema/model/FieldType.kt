package com.fluxion.schema.model

/**
 * Canonical logical field type enum — the least-common-denominator union of
 * JSON Schema, Protobuf, and Avro primitive + compound types.
 *
 * Format-specific extractors map their native types onto these values so the
 * admin UI and rule engine can treat equivalent wire types uniformly:
 *
 * - JSON Schema `"string"`          → [STRING]
 * - JSON Schema `"integer"`         → [INTEGER] (32-bit-ish, but rules treat it as generic number)
 * - Protobuf `int64` / `fixed64`    → [LONG]
 * - JSON Schema `"number"` / Avro `double` → [DOUBLE]
 * - JSON Schema `"object"`         → [OBJECT]
 * - JSON Schema `"array"`          → [ARRAY]
 * - Protobuf nested `message`      → [MESSAGE] (logically equivalent to OBJECT,
 *                                         but the tag helps the UI offer
 *                                         Protobuf-specific affordances)
 * - Avro `record`                  → [RECORD]  (same idea for Avro)
 * - [ANY] — "explicitly untyped" — used when a schema field declares no type
 *       (JSON Schema permits this) and we want to distinguish it from NULL.
 */
enum class FieldType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL,
    ANY,
    MESSAGE,
    RECORD
}
