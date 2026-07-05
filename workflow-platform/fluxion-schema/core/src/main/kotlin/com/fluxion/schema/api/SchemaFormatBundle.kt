package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

/**
 * 将某一 [SchemaFormat] 的全套 SPI 组件捆绑在一起。
 *
 * 每个格式模块（json / avro / protobuf / 自定义）只需提供一个 Bundle Bean，
 * 由 [com.fluxion.schema.DefaultSchemaManager] 和 [SchemaDataProviderRegistry]
 * 自动拆分构建 O(1) 路由表。
 *
 * 未提供的组件为 null，该格式在对应能力上不可用。
 */
data class SchemaFormatBundle(
    val format: SchemaFormat,
    val parser: SchemaParser? = null,
    val validator: SchemaValidator? = null,
    val extractor: SchemaFieldExtractor? = null,
    val codec: SchemaCodec? = null,
    val dataProvider: SchemaDataProvider? = null
)
