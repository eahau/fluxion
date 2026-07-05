package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult

/**
 * Schema 管理门面，提供统一的 Schema 解析、校验、字段提取、引用解析、编解码能力。
 */
interface SchemaManager {

    /**
     * 按格式解析 Schema。
     */
    fun parse(format: SchemaFormat, raw: String): Schema

    /**
     * 解析 JSON Schema。
     */
    fun parseJson(raw: String): Schema

    /**
     * 校验数据是否符合 Schema。
     */
    fun validate(schema: Schema, data: Any?): ValidationResult

    /**
     * 按注册名校验数据。
     */
    fun validateByName(schemaName: String, data: Any?): ValidationResult

    /**
     * 提取 Schema 字段树。
     */
    fun extractFields(schema: Schema): List<SchemaField>

    /**
     * 解析引用字符串。
     */
    fun resolveRef(ref: String): Schema?

    /**
     * 获取指定格式的编解码器。
     *
     * @throws IllegalArgumentException 若没有可用的 codec
     */
    fun codec(format: SchemaFormat): SchemaCodec
}
