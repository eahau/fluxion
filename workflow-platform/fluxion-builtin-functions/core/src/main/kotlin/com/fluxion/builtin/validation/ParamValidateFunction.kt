package com.fluxion.builtin.validation

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas
import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionResult
import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import org.slf4j.LoggerFactory

/**
 * 内置参数校验函数（builtin:paramValidate）
 *
 * 基于 fluxion-schema:core 提供的统一 Schema 能力进行校验，
 * 不再直接依赖 networknt/json-schema-validator。
 */
class ParamValidateFunction : WorkflowFunction<Any?>, BuiltinFunction {

    companion object {
        private val SCHEMA_MANAGER = DefaultSchemaManager(
            bundles = listOf(
                com.fluxion.schema.api.SchemaFormatBundle(
                    format = com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA,
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
                log.warn("ParamValidate failed: {}", result.errors)
                throw InvalidParamException(result.errors)
            }

            return FunctionResult.success(input.directInput)
        } catch (e: InvalidParamException) {
            throw e
        } catch (e: Exception) {
            throw InvalidParamException(listOf("Schema validation error: ${e.message}"))
        }
    }

    override fun meta() = BuiltinFunctionMetas.PARAM_VALIDATE
}
