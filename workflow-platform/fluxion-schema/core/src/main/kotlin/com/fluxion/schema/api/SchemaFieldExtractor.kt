package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField

/**
 * Schema 字段提取器，将不同格式的 Schema 转换为统一的 [SchemaField] 树。
 *
 * 格式路由由 [ExtractorBinding] 在 Spring 装配层声明，SPI 本身不感知格式。
 */
interface SchemaFieldExtractor {

    /**
     * 提取字段列表。
     *
     * @param schema 已编译的 Schema
     * @return 字段树（仅包含顶层字段，嵌套字段在 [SchemaField.nestedFields] 中）
     */
    fun extractFields(schema: Schema): List<SchemaField>
}
