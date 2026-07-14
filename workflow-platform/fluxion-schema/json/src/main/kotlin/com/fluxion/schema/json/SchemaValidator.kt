package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.exception.SchemaValidationException
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.error
import java.util.concurrent.ConcurrentHashMap

class SchemaValidator @JvmOverloads constructor(
    private val schemaManager: SchemaManager? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val parser = JsonSchemaParser()
    private val validator = JsonSchemaValidator()
    private val objectMapper = ObjectMapper()

    private val schemaCache = ConcurrentHashMap<Int, Schema>()

    fun validate(schema: Any?, data: Any?): ValidationResult {
        return validate(schema, data, null)
    }

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

    fun validateStrict(schema: Any?, data: Any?) {
        validateStrict(schema, data, null)
    }

    fun validateStrict(schema: Any?, data: Any?, format: SchemaFormat?) {
        if (schema == null || isEmptySchema(schema)) return
        val result = validate(schema, data, format)
        if (!result.valid) {
            throw SchemaValidationException("Output schema validation failed", result.errors)
        }
    }

    private fun isEmptySchema(schema: Any): Boolean {
        val str = if (schema is String) schema.trim() else objectMapper.writeValueAsString(schema)
        return str.isBlank() || str == "{}" || str == "null"
    }

    private fun compileSchema(schema: Any): Schema {
        val hashKey = System.identityHashCode(schema)
        return schemaCache.computeIfAbsent(hashKey) {
            parser.parse(null, schema)
        }
    }

    private fun compileSchemaMultiFormat(schema: Any, format: SchemaFormat = SchemaFormat.JSON_SCHEMA): Schema {
        val hashKey = System.identityHashCode(schema) * 31 + format.hashCode()
        return schemaCache.computeIfAbsent(hashKey) {
            schemaManager!!.parse(format,
                schema as? String ?: objectMapper.writeValueAsString(schema)
            )
        }
    }
}