package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationError
import com.fluxion.schema.model.ValidationResult
import com.fluxion.schema.util.JsonUtil
import org.slf4j.LoggerFactory

/**
 * JSON Schema 校验器实现。
 *
 * 基于 networknt/json-schema-validator，支持 V7 规范。
 * 校验结果包含结构化错误信息（路径、错误类型、Schema 路径），
 * 便于前端做字段级错误展示。
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
            log.error("Schema validation error: ${e.message}", e)
            ValidationResult.fail(listOf("Schema validation internal error: ${e.message}"))
        }
    }
}
