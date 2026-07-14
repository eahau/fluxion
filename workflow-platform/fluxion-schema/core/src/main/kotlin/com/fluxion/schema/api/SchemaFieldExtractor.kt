package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField

/**
 * Schema field-extractor SPI — flattens a format-specific compiled [Schema]
 * into the unified [SchemaField] tree consumed by the admin UI, the rule
 * engine, and parameter forms.
 *
 * Extractors MUST recursively expand nested compound types (JSON Schema
 * `properties`, Protobuf nested MESSAGE fields, Avro RECORD fields) into
 * [SchemaField.nestedFields]; primitive leaf nodes carry an empty list.
 *
 * One implementation per schema format; bound via [SchemaFormatBundle].
 */
interface SchemaFieldExtractor {

    /**
     * Extracts the top-level fields of [schema].
     *
     * @return list of top-level [SchemaField] entries. Nested fields live
     *         inside each entry's `nestedFields` — there is NO flat list.
     */
    fun extractFields(schema: Schema): List<SchemaField>
}
