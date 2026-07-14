package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationResult

/**
 * Unified facade over every schema capability — parsing, validation,
 * field extraction, reference resolution, and codec lookup.
 *
 * `SchemaManager` is what application-level code (workflow nodes, API
 * controllers, admin-UI services) talks to. The individual SPIs (parser,
 * validator, extractor, codec, registry, resolver) are deliberately
 * package-private implementation details; callers go through the manager.
 *
 * The default implementation ([DefaultSchemaManager]) is constructed from
 * a list of [SchemaFormatBundle] entries, one per format. Spring Boot
 * auto-config collects all bundle beans from the application context and
 * wires them.
 */
interface SchemaManager {

    /** Parses [raw] according to [format] and returns the compiled schema. */
    fun parse(format: SchemaFormat, raw: String): Schema

    /** Convenience shortcut for `parse(JSON_SCHEMA, raw)`. */
    fun parseJson(raw: String): Schema

    /** Validates [data] against [schema] and returns the result. */
    fun validate(schema: Schema, data: Any?): ValidationResult

    /** Looks up [schemaName] in the configured [SchemaRegistry] and validates. */
    fun validateByName(schemaName: String, data: Any?): ValidationResult

    /** Extracts the unified field tree for [schema]. */
    fun extractFields(schema: Schema): List<SchemaField>

    /** Resolves a `format:name` reference string via the configured [SchemaResolver]. */
    fun resolveRef(ref: String): Schema?

    /**
     * Returns the codec registered for [format].
     *
     * @throws IllegalArgumentException if no codec is bound for the format.
     */
    fun codec(format: SchemaFormat): SchemaCodec
}
