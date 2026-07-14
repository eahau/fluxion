/**
 * Avro-backed [SchemaValidator] that validates runtime data against a compiled
 * Avro schema using a round-trip binary encode/decode strategy.
 *
 * Because Avro has no native runtime "validate" API, the validator converts
 * the incoming value (GenericRecord, JSON string, Map/POJO) into a
 * [GenericRecord], writes it using `GenericDatumWriter` to a binary stream,
 * then reads it back with `GenericDatumReader`. Any schema mismatch surfaces
 * as an exception which we translate into a [ValidationResult].
 */
package com.fluxion.schema.avro

import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationError
import com.fluxion.schema.model.ValidationResult
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.io.ByteArrayOutputStream

/**
 * Validates data against an Avro schema using round-trip binary ser/de.
 *
 * ### Supported input shapes
 * * [GenericRecord] — directly serialised without conversion.
 * * [String] — treated as Avro JSON encoding and parsed via [jsonDecoder].
 * * `Map` / POJO — converted to GenericRecord via [AvroDataConverter] first.
 *
 * Empty schemas (isEmpty == true) short-circuit to [ValidationResult.ok] so
 * callers that accept "any" data are not forced to define a schema.
 */
class AvroSchemaValidator : SchemaValidator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun validate(schema: Schema, data: Any?): ValidationResult {
        require(schema.format == SchemaFormat.AVRO) {
            "AvroSchemaValidator only supports AVRO format, got ${schema.format}"
        }

        if (schema.isEmpty) {
            return ValidationResult.ok()
        }

        val avroSchema = schema.parsed as? org.apache.avro.Schema
            ?: return ValidationResult.failDetailed(listOf(
                ValidationError(path = "", message = "Schema parsed object is not an Avro Schema", errorType = "schema")
            ))

        return try {
            if (data is GenericRecord) {
                validateViaRoundTrip(avroSchema, data)
            } else if (data is String) {
                // Avro JSON payloads may include union wrappers; the JSON
                // decoder natively understands them so no pre-processing is
                // needed before round-tripping.
                validateViaJsonDecoder(avroSchema, data)
            } else {
                val record = AvroDataConverter.toGenericRecord(data, avroSchema)
                validateViaRoundTrip(avroSchema, record)
            }
        } catch (e: Exception) {
            log.warn(e) { "Avro schema validation failed: ${e.message}" }
            ValidationResult.failDetailed(listOf(
                ValidationError(
                    path = extractPathFromException(e),
                    message = "Avro validation error: ${e.message}",
                    errorType = e::class.simpleName
                )
            ))
        }
    }

    /**
     * Best-effort extraction of a dotted field path from Avro exception text.
     *
     * Avro messages typically embed field names as `"field 'foo'"` or similar.
     * When no pattern matches, an empty string is returned so callers can
     * still attribute the error to the root value.
     */
    private fun extractPathFromException(e: Exception): String {
        val msg = e.message ?: return ""
        val fieldMatch = Regex("""field\s+'?([\w.]+)'?""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch != null) return fieldMatch.groupValues[1]
        return ""
    }

    private fun validateViaRoundTrip(schema: org.apache.avro.Schema, record: GenericRecord): ValidationResult {
        val baos = ByteArrayOutputStream()
        val writer = GenericDatumWriter<GenericRecord>(schema)
        val encoder = EncoderFactory.get().binaryEncoder(baos, null)
        writer.write(record, encoder)
        encoder.flush()

        val reader = GenericDatumReader<GenericRecord>(schema)
        val decoder = DecoderFactory.get().binaryDecoder(baos.toByteArray(), null)
        reader.read(null, decoder)
        return ValidationResult.ok()
    }

    private fun validateViaJsonDecoder(schema: org.apache.avro.Schema, json: String): ValidationResult {
        val reader = GenericDatumReader<Any>(schema)
        val decoder = DecoderFactory.get().jsonDecoder(schema, json)
        reader.read(null, decoder)
        return ValidationResult.ok()
    }
}
