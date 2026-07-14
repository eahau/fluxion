package com.fluxion.schema.exception

/**
 * Thrown by strict-mode schema validation (see
 * [com.fluxion.schema.api.SchemaValidator.validateStrict]) when the input
 * data fails one or more schema constraints.
 *
 * The [errors] list carries the same plain-string error messages that a
 * non-strict [com.fluxion.schema.model.ValidationResult] would return, so
 * callers can choose between "check result" and "catch exception" styles
 * without losing information.
 *
 * @property errors flat list of validation failure messages; order is not
 *   guaranteed to match any particular schema traversal.
 */
class SchemaValidationException(
    message: String,
    val errors: List<String>
) : RuntimeException(message)
