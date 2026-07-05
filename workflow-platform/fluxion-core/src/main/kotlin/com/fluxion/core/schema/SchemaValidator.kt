package com.fluxion.core.schema

import com.fluxion.core.exception.SchemaValidationException
import com.fluxion.core.value.ValidationResult
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * JSON Schema 校验器 — 已迁移至 [com.fluxion.schema.api.SchemaManager]。
 *
 * 此类作为 fluxion-core 的兼容适配器保留。
 * - 若构造时传入 [SchemaManager]，则委托其多格式能力进行解析与校验；
 * - 否则回退到纯 JSON Schema 模式（兼容旧代码）。
 *
 * 新增代码建议直接使用 [com.fluxion.schema.api.SchemaManager]。
 */
class SchemaValidator @JvmOverloads constructor(
    private val schemaManager: SchemaManager? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val parser = JsonSchemaParser()
    private val validator = JsonSchemaValidator()

    /**
     * 缓存已编译的 Schema（按原始对象 identityHashCode 去重）。
     */
    private val schemaCache = ConcurrentHashMap<Int, Schema>()

    /**
     * 校验数据是否符合 Schema。
     *
     * schema 为 null 或空 Schema 时直接返回 ok（不校验）。
     * 当有 SchemaManager 时支持多格式，否则仅支持 JSON Schema。
     *
     * @param schema  Schema（String 或 Map）
     * @param data    待校验数据
     * @return 校验结果（valid + errors）
     */
    fun validate(schema: Any?, data: Any?): ValidationResult {
        return validate(schema, data, null)
    }

    /**
     * 格式感知的 Schema 校验。
     *
     * @param schema  Schema（String 或 Map）
     * @param data    待校验数据
     * @param format  Schema 格式（null 时默认 JSON Schema，向后兼容）
     * @return 校验结果（valid + errors）
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
            log.error("Schema validation error: ${e.message}", e)
            ValidationResult.fail(listOf("Schema validation internal error: ${e.message}"))
        }
    }

    /**
     * 严格模式校验：不通过时抛出 [SchemaValidationException]。
     * 用于工作流级 outputSchema 契约校验。
     */
    fun validateStrict(schema: Any?, data: Any?) {
        validateStrict(schema, data, null)
    }

    /**
     * 格式感知的严格模式校验：不通过时抛出 [SchemaValidationException]。
     *
     * @param format Schema 格式（null 时默认 JSON Schema，向后兼容）
     */
    fun validateStrict(schema: Any?, data: Any?, format: SchemaFormat?) {
        if (schema == null || isEmptySchema(schema)) return
        val result = validate(schema, data, format)
        if (!result.valid) {
            throw SchemaValidationException("Output schema validation failed", result.errors)
        }
    }

    /**
     * 判断是否为空 Schema（null、空字符串、空 JSON "{}" 等）
     */
    private fun isEmptySchema(schema: Any): Boolean {
        val str = if (schema is String) schema.trim() else com.fluxion.core.util.JsonUtil.serialize(schema)
        return str.isBlank() || str == "{}" || str == "null"
    }

    /** 编译并缓存 Schema（JSON Schema 模式）。 */
    private fun compileSchema(schema: Any): Schema {
        val hashKey = System.identityHashCode(schema)
        return schemaCache.computeIfAbsent(hashKey) {
            parser.parse(null, schema)
        }
    }

    /**
     * 编译并缓存 Schema（多格式模式）。
     *
     * @param schema Schema 原始内容（String 或 Map）
     * @param format Schema 格式（决定使用哪个 Parser/Validator）
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
