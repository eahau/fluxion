package com.fluxion.core.schema

import com.fluxion.core.exception.SchemaValidationException
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult
import org.slf4j.LoggerFactory
import org.slf4j.error
import java.util.concurrent.ConcurrentHashMap

/**
 * Validates payloads against JSON Schema (and, optionally, other
 * SchemaManager-backed formats).
 *
 * Operates in two modes:
 *  - **Bare (no SchemaManager injected):** Falls back to a bundled
 *    Jackson-backed `JsonSchemaValidator` plus `JsonSchemaParser`.
 *    Sufficient for local dev and tests, no admin console dependency.
 *  - **With SchemaManager injected:** Delegates parse + validate to
 *    the manager so that Protobuf/Avro/custom formats are also
 *    honoured, and reference schemas (`schema:name`) resolve cleanly.
 *
 * Compiled schemas are cached by identity-hashcode of the raw input
 * plus format, so repeated calls with the same payload definition
 * do not re-parse.
 */
class SchemaValidator @JvmOverloads constructor(
    private val schemaManager: SchemaManager? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val parser = JsonSchemaParser()
    private val validator = JsonSchemaValidator()

    /** identityHashCode(schemaAny) → compiled Schema. */
    private val schemaCache = ConcurrentHashMap<Int, Schema>()

    /**
     * Validate `data` against `schema` using the default JSON Schema
     * format.  Returns a [ValidationResult] — callers decide whether
     * to throw, log, or attach to an HTTP 400 envelope.
     */
    fun validate(schema: Any?, data: Any?): ValidationResult {
        return validate(schema, data, null)
    }

    /**
     * Validate `data` against `schema` with an explicit [SchemaFormat].
     *
     * When a SchemaManager is present it is used for both parse and
     * validate; otherwise the bundled JSON Schema validator handles
     * the call.  `null` or empty schemas short-circuit to `ok()`.
     *
     * @param schema raw schema — String, Map, or (rarely) pre-parsed.
     * @param data   payload to validate.
     * @param format declared format; `null` ⇒ JSON Schema (legacy default).
     */
    fun validate(schema: Any?, data: Any?, format: SchemaFormat?): ValidationResult {
        if (schema == null || isEmptySchema(schema)) return ValidationResult.ok()
        return try {
            if (schemaManager != null) {
                val compiled = compileSchemaMultiFormat(schema, format ?: SchemaFormat.JSON_SCHEMA)
                schemaManager.validate(compiled, data)
            } else {
                val compiled = compileSchema(schema)
                validator.validate(compiled, data)
            }
        } catch (e: Exception) {
            log.error(e) { "Schema validation error: ${e.message}" }
            ValidationResult.fail(listOf("Schema validation internal error: ${e.message}"))
        }
    }

    /**
     * Strict variant of [validate] that throws [SchemaValidationException]
     * if the result is invalid.
     *
     * Used on output paths (e.g. after a function returns) where a
     * contract violation must fail-fast rather than silently degrade.
     */
    fun validateStrict(schema: Any?, data: Any?) {
        validateStrict(schema, data, null)
    }

    /** @see validateStrict */
    fun validateStrict(schema: Any?, data: Any?, format: SchemaFormat?) {
        if (schema == null || isEmptySchema(schema)) return
        val result = validate(schema, data, format)
        if (!result.valid) {
            throw SchemaValidationException("Output schema validation failed", result.errors)
        }
    }

    /**
     * True if the schema payload carries no constraints.
     *
     * Accepts raw JSON strings (`"{}"`, `"null"`, blank) and Map/Node
     * forms — compares against the serialised JSON form for safety.
     */
    private fun isEmptySchema(schema: Any): Boolean {
        val str = if (schema is String) schema.trim() else com.fluxion.core.util.JsonUtil.serialize(schema)
        return str.isBlank() || str == "{}" || str == "null"
    }

    /** Compile and cache a raw JSON Schema payload. */
    private fun compileSchema(schema: Any): Schema {
        val hashKey = System.identityHashCode(schema)
        return schemaCache.computeIfAbsent(hashKey) {
            parser.parse(null, schema)
        }
    }

    /**
     * Compile and cache a schema payload for an arbitrary format via
     * SchemaManager.  Cache key is identity-hash × format-hash so the
     * same Map parsed once as JSON Schema and once as Protobuf does
     * not collide.
     */
    private fun compileSchemaMultiFormat(schema: Any, format: SchemaFormat = SchemaFormat.JSON_SCHEMA): Schema {
        val hashKey = System.identityHashCode(schema) * 31 + format.hashCode()
        return schemaCache.computeIfAbsent(hashKey) {
            schemaManager!!.parse(format,
                schema as? String ?: com.fluxion.core.util.JsonUtil.serialize(schema)
            )
        }
    }
}
