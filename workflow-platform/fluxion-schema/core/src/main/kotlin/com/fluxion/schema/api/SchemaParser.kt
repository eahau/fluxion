package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

/**
 * Schema parser SPI — converts raw schema source text (or a structured object)
 * into a compiled, format-specific [Schema] envelope.
 *
 * One implementation exists per format (JSON Schema, Protobuf, Avro). The
 * core module never binds parsers to formats directly; the Spring Boot
 * auto-config module assembles format bundles and the [DefaultSchemaManager]
 * routes by [SchemaFormat] at runtime.
 */
interface SchemaParser {

    /**
     * Parses a schema from raw source text.
     *
     * @param name registered lookup name (optional — `null` for inline
     *              schemas such as node-level rules).
     * @param raw  the schema source: a JSON string for JSON Schema / Avro /
     *              Protobuf FileDescriptorProto JSON.
     * @return a compiled [Schema] whose `parsed` field carries the
     *         format-specific validator object.
     */
    fun parse(name: String?, raw: String): Schema

    /**
     * Parses a schema from a structured object (Map, POJO, JsonNode, etc.).
     *
     * The default implementation round-trips through JSON serialization;
     * format-specific parsers can override for efficiency when the input
     * is already in a convenient shape.
     *
     * @param name registered lookup name, or `null` for inline schemas.
     * @param raw  structured schema object.
     */
    fun parse(name: String?, raw: Any): Schema
}
