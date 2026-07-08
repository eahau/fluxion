package com.fluxion.schema.model

/**
 * Unified schema envelope shared across every schema format (JSON Schema,
 * Protobuf, Avro, and any custom formats registered via extension modules).
 *
 * A [Schema] is the parsed+cached unit that flows through the validation,
 * field-extraction, and codec pipelines. The `parsed` field carries the
 * format-specific compiled representation — for JSON Schema this is a
 * `com.networknt.schema.JsonSchema`, for Protobuf it's a `Descriptors.Descriptor`,
 * etc. Callers never inspect `parsed` directly; format-specific SPIs handle
 * it behind the typed interfaces in `com.fluxion.schema.api`.
 *
 * @property name   registered lookup name; `null` for inline / anonymous schemas
 *                   that were parsed ad-hoc (e.g. node-level validation rules).
 * @property format schema format code — routes the instance to the right SPI.
 * @property raw    original source text (JSON string / `.proto` JSON / `.avsc`
 *                   JSON) retained for error messages and round-tripping.
 * @property parsed format-specific compiled object — opaque to generic code.
 */
data class Schema(
    val name: String?,
    val format: SchemaFormat,
    val raw: String,
    val parsed: Any
) {
    /**
     * True when this schema imposes **no** constraints — i.e. validation
     * should short-circuit to "always pass".
     *
     * Three forms are recognized as empty, regardless of format:
     * - blank / whitespace-only `raw`
     * - the literal string `"{}"` (empty JSON object, ubiquitous across the
     *   three built-in formats)
     * - the literal string `"null"` (JSON Schema's way of spelling "no schema")
     *
     * Keeping this check centralized means every validator in every format
     * module skips work for trivially-empty schemas with a single cheap
     * string comparison.
     */
    val isEmpty: Boolean
        get() {
            val trimmed = raw.trim()
            return trimmed.isBlank() || trimmed == "{}" || trimmed == "null"
        }
}
