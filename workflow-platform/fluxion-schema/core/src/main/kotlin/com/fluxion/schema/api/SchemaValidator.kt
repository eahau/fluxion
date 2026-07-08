package com.fluxion.schema.api

import com.fluxion.schema.exception.SchemaValidationException
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.ValidationResult

/**
 * Schema validator SPI — checks a runtime value against the constraints
 * declared in a compiled [Schema] and returns a structured [ValidationResult].
 *
 * One implementation per format (JSON Schema, Protobuf, Avro). Empty schemas
 * (`Schema.isEmpty == true`) should short-circuit to `ValidationResult.ok()`
 * without invoking the underlying validator library.
 */
interface SchemaValidator {

    /**
     * Validates [data] against [schema] and returns the result.
     *
     * Must never throw for validation failures — callers that want exceptions
     * use [validateStrict] instead. Unexpected parser/library exceptions MAY
     * propagate (they indicate a corrupt schema or broken validator impl,
     * not bad user input).
     */
    fun validate(schema: Schema, data: Any?): ValidationResult

    /**
     * Strict variant — same semantics as [validate] but throws
     * [SchemaValidationException] on any failure. Convenience for call sites
     * that prefer exception-style control flow (e.g. at workflow function
     * boundaries where failing the node is the right outcome).
     */
    fun validateStrict(schema: Schema, data: Any?) {
        val result = validate(schema, data)
        if (!result.valid) {
            throw SchemaValidationException("Schema validation failed", result.errors)
        }
    }
}
