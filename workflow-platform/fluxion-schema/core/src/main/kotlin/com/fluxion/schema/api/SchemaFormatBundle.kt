/**
 * Aggregates every SPI implementation that exists for a single [SchemaFormat].
 *
 * Each format sub-module (json / avro / protobuf / custom) ships one bundle
 * bean (often via Spring auto-configuration), which [DefaultSchemaManager]
 * and the data-provider registry then split apart into O(1) lookup tables.
 * Leaving a component `null` simply indicates the capability is not offered
 * for that format — the manager surface an appropriate error for callers who
 * try to use the missing SPI.
 */
package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

/**
 * Immutable value object grouping the six format-specific SPIs together so
 * Spring/DI contexts can contribute capabilities per format with one bean.
 *
 * @property format       the schema format this bundle describes.
 * @property parser       compiles raw text into a parsed schema object.
 * @property validator    validates runtime payloads against a compiled schema.
 * @property extractor    walks a compiled schema to produce a [SchemaField] tree.
 * @property codec        binary/JSON serialisation codec for schema messages.
 * @property dataProvider generic field access on values of this format.
 */
data class SchemaFormatBundle(
    val format: SchemaFormat,
    val parser: SchemaParser? = null,
    val validator: SchemaValidator? = null,
    val extractor: SchemaFieldExtractor? = null,
    val codec: SchemaCodec? = null,
    val dataProvider: SchemaDataProvider? = null
)
