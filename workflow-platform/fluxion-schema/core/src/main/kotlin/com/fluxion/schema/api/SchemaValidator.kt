package com.fluxion.schema.api

import com.fluxion.schema.exception.SchemaValidationException
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.ValidationResult

/**
 * Schema 校验器，校验数据是否符合指定 Schema 约束。
 *
 * 格式路由由 [ValidatorBinding] 在 Spring 装配层声明，SPI 本身不感知格式。
 */
interface SchemaValidator {

    /**
     * 校验数据是否符合 Schema。
     *
     * @param schema 已编译的 Schema
     * @param data 待校验数据
     * @return 校验结果
     */
    fun validate(schema: Schema, data: Any?): ValidationResult

    /**
     * 严格模式校验：不通过时抛出 [SchemaValidationException]。
     */
    fun validateStrict(schema: Schema, data: Any?) {
        val result = validate(schema, data)
        if (!result.valid) {
            throw SchemaValidationException("Schema validation failed", result.errors)
        }
    }
}
