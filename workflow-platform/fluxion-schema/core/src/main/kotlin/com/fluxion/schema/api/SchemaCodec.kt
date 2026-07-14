package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

/**
 * Schema-aware codec SPI — serializes structured data to / from wire formats
 * using a schema as the type guide.
 *
 * Different formats use different encoding strategies:
 * - **JSON Schema** — standard Jackson JSON (binary form is just UTF-8 JSON).
 * - **Avro**       — avro-java encoder (binary) or Avro JSON (text).
 * - **Protobuf**   — `DynamicMessage.toByteArray()` (binary) or the proto3
 *                     canonical JSON format (text).
 *
 * Callers that only care about JSON can use [toJson] / [fromJson]; transport
 * layers (RPC, persistence) that need compact bytes use [serialize] /
 * [deserialize]. The binary form is *not* guaranteed to match the JSON form
 * parsed by [fromJson] — they are separate pipelines.
 *
 * One implementation per schema format; bound via [SchemaFormatBundle].
 */
interface SchemaCodec {

    /**
     * Serializes [data] to a compact byte-array wire format.
     *
     * @param data   structured value — usually a Map, POJO, or format-native
     *                object (e.g. `DynamicMessage` / `GenericRecord`).
     * @param schema schema driving the encoding choices (type conversions,
     *                defaults, field ordering).
     */
    fun serialize(data: Any?, schema: Schema): ByteArray

    /**
     * Deserializes a binary wire payload produced by [serialize].
     *
     * @param bytes  wire bytes (must match the format this codec handles).
     * @param schema schema driving the decode — MUST be the same format that
     *                produced the bytes.
     */
    fun deserialize(bytes: ByteArray, schema: Schema): Any?

    /**
     * Serializes [data] to a JSON string representation.
     *
     * For JSON Schema this is the Jackson default; for Protobuf/Avro it is
     * the format's canonical JSON dialect, which can differ subtly from
     * Jackson (e.g. int64 as string, enum handling).
     */
    fun toJson(data: Any?, schema: Schema): String

    /**
     * Deserializes a JSON string produced by [toJson] (or a compatible source)
     * into structured data, driven by [schema].
     */
    fun fromJson(json: String, schema: Schema): Any?
}
