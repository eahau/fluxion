/**
 * JSON Schema (draft V7) validator backed by the networknt
 * `json-schema-validator` library.
 *
 * Translates networknt's `Set<ValidationMessage>` output into the project's
 * uniform [ValidationResult] structure, preserving instance/schema locations
 * so UIs can render per-field errors next to the originating input control.
 */
package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationError
import com.fluxion.schema.model.ValidationResult
import com.fluxion.schema.util.JsonUtil
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Validates Jackson-compatible data trees against a pre-compiled networknt
 * JsonSchema.
 *
 * Empty schemas short-circuit to OK; this mirrors the "no constraints"
 * semantics of `{}` in JSON Schema and keeps validation for ad-hoc, weakly
 * structured inputs consistent.
 */
class JsonSchemaValidator : SchemaValidator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun validate(schema: Schema, data: Any?): ValidationResult {
        require(schema.format == SchemaFormat.JSON_SCHEMA) {
            "JsonSchemaValidator only supports JSON_SCHEMA, got ${schema.format}"
        }

        if (schema.isEmpty) {
            return ValidationResult.ok()
        }

        return try {
            val jsonSchema = schema.parsed as com.networknt.schema.JsonSchema
            val dataNode = JsonUtil.valueToTree(data)
            val errors = jsonSchema.validate(dataNode)
            if (errors.isEmpty()) {
                ValidationResult.ok()
            } else {
                val details = errors.map { msg ->
                    ValidationError(
                        path = msg.instanceLocation?.toString() ?: "",
                        message = msg.message,
                        errorType = msg.type?.toString(),
                        schemaPath = msg.schemaLocation?.toString()
                    )
                }
                ValidationResult.failDetailed(details)
            }
        } catch (e: Exception) {
            log.error(e) { "Schema validation error: ${e.message}" }
            ValidationResult.fail(listOf("Schema validation internal error: ${e.message}"))
        }
    }
}
