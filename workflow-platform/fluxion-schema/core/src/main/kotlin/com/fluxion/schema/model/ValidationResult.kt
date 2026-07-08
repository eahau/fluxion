package com.fluxion.schema.model

/**
 * Structured validation error carrying both a human-readable message and the
 * machine-readable pointers needed by the admin UI to highlight the exact
 * failing field.
 *
 * The dual `path` / `schemaPath` pair follows the JSON Schema convention —
 * `path` is where the BAD VALUE lives in the user data, `schemaPath` is
 * where the VIOLATED CONSTRAINT lives in the schema document. For formats
 * without a schema-pointer convention (Protobuf, Avro) `schemaPath` may be
 * `null`; callers MUST treat `schemaPath` as optional.
 *
 * @property path       dotted/bracketed path in the INPUT data, e.g.
 *                       `"user.addresses[0].city"` — used by form widgets to
 *                       paint inline errors.
 * @property message    human-readable error text (localization happens
 *                       upstream; values here are always US English defaults).
 * @property errorType  stable error code, e.g. `"required"`, `"type"`,
 *                       `"format"`, `"pattern"` — used for programmatic
 *                       error classification. `null` for old validators that
 *                       don't produce structured codes.
 * @property schemaPath JSON-pointer-style path in the SCHEMA document, or
 *                       `null` for formats that don't support it.
 */
data class ValidationError(
    val path: String,
    val message: String,
    val errorType: String? = null,
    val schemaPath: String? = null
)

/**
 * Result of running a [SchemaValidator] against a piece of data.
 *
 * Carries THREE representations of the outcome so old and new callers all
 * work without migration:
 * 1. `valid`           — boolean quick-check (all callers use this).
 * 2. `errors`          — plain `List<String>` legacy shape (kept for
 *                         backward-compat with code written before structured
 *                         errors existed).
 * 3. `details`         — structured [ValidationError] list, the modern
 *                         shape used by the admin UI for field-level
 *                         highlighting.
 *
 * The companion factory methods ensure that `errors` and `details` always
 * stay in sync — callers should NEVER construct a ValidationResult directly;
 * use [ok], [fail], or [failDetailed] instead.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val details: List<ValidationError> = emptyList()
) {
    companion object {
        /** @return a successful result with no errors. */
        @JvmStatic
        fun ok() = ValidationResult(true, emptyList(), emptyList())

        /**
         * Creates a failed result from legacy plain-text errors.
         *
         * Each string is auto-wrapped into a [ValidationError] with an empty
         * `path` so new callers that only inspect `details` still see the
         * errors even when a legacy validator produced only strings.
         */
        @JvmStatic
        fun fail(errors: List<String>) = ValidationResult(
            false,
            errors.toList(),
            errors.map { ValidationError(path = "", message = it) }
        )

        /**
         * Creates a failed result from structured [ValidationError] details.
         *
         * The legacy `errors` list is auto-derived by extracting the `message`
         * field so legacy callers that only read `errors` don't lose data.
         */
        @JvmStatic
        fun failDetailed(details: List<ValidationError>) = ValidationResult(
            false,
            details.map { it.message },
            details
        )
    }
}
