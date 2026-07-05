package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.model.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream

/**
 * Avro Schema 编解码器。
 *
 * 支持 Avro 的 JSON 编码和 Binary 编码两种序列化方式：
 * - [serialize] / [deserialize]：使用 Avro Binary 编码（高效紧凑）
 * - [toJson] / [fromJson]：使用 Avro JSON 编码（可读性强）
 */
class AvroSchemaCodec : SchemaCodec {

    override fun serialize(data: Any?, schema: Schema): ByteArray {
        val avroSchema = schema.parsed as org.apache.avro.Schema
        val datum = AvroDataConverter.toGenericRecord(data, avroSchema)

        val baos = ByteArrayOutputStream()
        val writer = GenericDatumWriter<GenericRecord>(avroSchema)
        val encoder = EncoderFactory.get().binaryEncoder(baos, null)
        writer.write(datum, encoder)
        encoder.flush()
        return baos.toByteArray()
    }

    override fun deserialize(bytes: ByteArray, schema: Schema): Any? {
        val avroSchema = schema.parsed as org.apache.avro.Schema
        val reader = GenericDatumReader<GenericRecord>(avroSchema)
        val decoder = DecoderFactory.get().binaryDecoder(bytes, null)
        return reader.read(null, decoder)
    }

    override fun toJson(data: Any?, schema: Schema): String {
        val avroSchema = schema.parsed as org.apache.avro.Schema
        val datum = AvroDataConverter.toGenericRecord(data, avroSchema)

        val baos = ByteArrayOutputStream()
        val writer = GenericDatumWriter<GenericRecord>(avroSchema)
        val encoder = EncoderFactory.get().jsonEncoder(avroSchema, baos)
        writer.write(datum, encoder)
        encoder.flush()
        return baos.toString(Charsets.UTF_8)
    }

    override fun fromJson(json: String, schema: Schema): Any? {
        val avroSchema = schema.parsed as org.apache.avro.Schema
        val reader = GenericDatumReader<GenericRecord>(avroSchema)
        val decoder = DecoderFactory.get().jsonDecoder(avroSchema, json)
        return reader.read(null, decoder)
    }
}
