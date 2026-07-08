/**
 * Protobuf-backed [SchemaValidator] that coerces runtime values into a
 * [DynamicMessage] built from the schema's compiled [Descriptors.Descriptor].
 *
 * Successful construction of a DynamicMessage is sufficient proof that the
 * data conforms to the schema — Protobuf rejects unknown fields, wrong
 * scalar types, and malformed nested messages during builder merge.
 */
package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaValidator
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.model.ValidationError
import com.fluxion.schema.model.ValidationResult
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Validates data against a Protobuf descriptor.
 *
 * ### Accepted input types
 * * [DynamicMessage] — descriptor compatibility is checked, then reused.
 * * [String] — Protobuf JSON format; parsed via [JsonFormat.parser].
 * * `Map` / arbitrary POJO — serialised via JsonUtil first, then merged.
 *
 * Note that Protobuf 3 has no `required` concept so missing fields always
 * validate. Unknown fields on the other hand are rejected by the default
 * JsonFormat parser which matches strict validation expectations.
 */
class ProtobufSchemaValidator : SchemaValidator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun validate(schema: Schema, data: Any?): ValidationResult {
        require(schema.format == SchemaFormat.PROTOBUF) {
            "ProtobufSchemaValidator only supports PROTOBUF format, got ${schema.format}"
        }

        if (schema.isEmpty) {
            return ValidationResult.ok()
        }

        val descriptor = schema.parsed as? Descriptors.Descriptor
            ?: return ValidationResult.failDetailed(listOf(
                ValidationError(
                    path = "",
                    message = "Schema parsed object is not a Protobuf Descriptor",
                    errorType = "schema"
                )
            ))

        if (data == null) {
            return ValidationResult.ok()
        }

        return try {
            // A DynamicMessage can only be built when all fields honour the
            // descriptor contract. Any structural mismatch throws, which we
            // translate into a typed ValidationResult below.
            val message = toDynamicMessage(data, descriptor)
            log.debug { "Protobuf schema [${descriptor.fullName}] validation passed" }
            ValidationResult.ok()
        } catch (e: Exception) {
            log.warn(e) { "Protobuf validation failed for [${descriptor.fullName}]: ${e.message}" }
            ValidationResult.failDetailed(listOf(
                ValidationError(
                    path = extractPathFromException(e),
                    message = "Protobuf validation error: ${e.message}",
                    errorType = "type"
                )
            ))
        }
    }

    /**
     * Coerce an arbitrary input value into a [DynamicMessage] conforming to
     * [descriptor].
     */
    private fun toDynamicMessage(data: Any, descriptor: Descriptors.Descriptor): DynamicMessage {
        return when (data) {
            is DynamicMessage -> {
                if (data.descriptorForType.fullName != descriptor.fullName) {
                    throw IllegalArgumentException(
                        "DynamicMessage type mismatch: expected ${descriptor.fullName}, " +
                        "got ${data.descriptorForType.fullName}"
                    )
                }
                data
            }

            is String -> {
                val builder = DynamicMessage.newBuilder(descriptor)
                JsonFormat.parser().merge(data, builder)
                builder.build()
            }

            else -> {
                // Generic maps / POJOs go through an intermediate JSON step —
                // JsonFormat is the only supported path to populate a
                // DynamicMessage without generated Java code.
                val json = com.fluxion.schema.util.JsonUtil.serialize(data)
                val builder = DynamicMessage.newBuilder(descriptor)
                JsonFormat.parser().merge(json, builder)
                builder.build()
            }
        }
    }

    /**
     * Best-effort field-path extraction from Protobuf parser exceptions.
     */
    private fun extractPathFromException(e: Exception): String {
        val msg = e.message ?: return ""
        val fieldMatch = Regex("""field\s+['"]?([\w.]+)['"]?""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch != null) return fieldMatch.groupValues[1]
        val fieldMatch2 = Regex("""([\w.]+)\s+field""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch2 != null) return fieldMatch2.groupValues[1]
        return ""
    }
}
