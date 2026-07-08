package com.fluxion.builtin.validation

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionResult
import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.api.SchemaFormatBundle
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import org.slf4j.LoggerFactory

/**
 * Built-in JSON-Schema based parameter validation (`builtin:paramValidate`).
 *
 * Validates `directInput` against the schema provided in `nodeParams.schema`
 * (either a JSON string or an already-deserialized Map). Passes the input
 * through unchanged on success; throws [InvalidParamException] populated
 * with the validation errors on failure.
 *
 * Uses the shared schema-manager SPI from fluxion-schema with a single
 * JSON-Schema format bundle registered statically in the companion — this
 * keeps the function independent of Spring wiring and lets it work in tests.
 */
class ParamValidateFunction : WorkflowFunction<Any?>, BuiltinFunction {

    companion object {
        private val SCHEMA_MANAGER = DefaultSchemaManager(
            bundles = listOf(
                SchemaFormatBundle(
                    format = SchemaFormat.JSON_SCHEMA,
                    parser = JsonSchemaParser(),
                    validator = JsonSchemaValidator(),
                    extractor = JsonSchemaFieldExtractor()
                )
            )
        )
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val schemaParam = input.requireParam<Any>("schema")

        try {
            val schemaJson = schemaParam as? String ?: JsonUtil.serialize(schemaParam)
            val schema = SCHEMA_MANAGER.parseJson(schemaJson)
            val result = SCHEMA_MANAGER.validate(schema, input.directInput)

            if (!result.valid) {
                log.warn { "ParamValidate failed: ${result.errors}" }
                throw InvalidParamException(result.errors)
            }

            return FunctionResult.success(input.directInput)
        } catch (e: InvalidParamException) {
            throw e
        } catch (e: Exception) {
            throw InvalidParamException(listOf("Schema validation error: ${e.message}"))
        }
    }

    override val functionName: String = "builtin:paramValidate"
}
