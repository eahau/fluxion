/**
 * Protobuf [SchemaParser] that compiles `FileDescriptorProto` JSON (as
 * produced by `protoc --descriptor_set_out --json_format`) into a
 * [Descriptors.Descriptor] wrapped in the unified [Schema] envelope.
 *
 * A single FileDescriptorProto often ships several message types. See
 * [selectMessageDescriptor] for the precedence rules used to pick the target.
 *
 * Blank / null raw inputs are normalised to the JSON object `"{}"`, yielding
 * an empty schema that validates everything — matching JSON Schema semantics.
 */
package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Compiles Protobuf FileDescriptor JSON into a runtime [Schema].
 *
 * ### Message selection precedence
 * 1. Exact full-name match when the caller provides `name`.
 * 2. Short-name (unqualified) match on the message.
 * 3. Recursive search across nested types.
 * 4. The first `messageType` in the file when no hint is given.
 */
class ProtobufSchemaParser : SchemaParser {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun parse(name: String?, raw: String): Schema {
        val normalized = normalizeRaw(raw)
        return try {
            val fileProtoBuilder = DescriptorProtos.FileDescriptorProto.newBuilder()
            JsonFormat.parser().merge(normalized, fileProtoBuilder)
            val fileProto = fileProtoBuilder.build()

            // Empty schemas (no message types defined) return the marker so
            // downstream validators can short-circuit validation.
            if (fileProto.messageTypeCount == 0) {
                return Schema(
                    name = name,
                    format = SchemaFormat.PROTOBUF,
                    raw = normalized,
                    parsed = EmptyProtobufDescriptor
                )
            }

            // Build with empty dependencies — this assumes the descriptor set
            // was produced with all imports inlined, which is the convention
            // for standalone `protoc --descriptor_set_out` outputs.
            val fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto, emptyArray())

            val messageDescriptor = selectMessageDescriptor(fileDescriptor, name)
                ?: throw IllegalArgumentException(
                    "Message type not found: ${name ?: "(first)"}. " +
                    "Available: ${fileDescriptor.messageTypes.map { it.fullName }}"
                )

            Schema(
                name = name ?: messageDescriptor.fullName,
                format = SchemaFormat.PROTOBUF,
                raw = normalized,
                parsed = messageDescriptor
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse Protobuf schema: ${e.message}" }
            throw IllegalArgumentException("Invalid Protobuf FileDescriptorProto JSON: ${e.message}", e)
        }
    }

    override fun parse(name: String?, raw: Any): Schema {
        val rawStr = when (raw) {
            is String -> raw
            is DescriptorProtos.FileDescriptorProto -> JsonFormat.printer().print(raw)
            is Descriptors.Descriptor -> JsonFormat.printer().print(raw.file.toProto())
            else -> com.fluxion.schema.util.JsonUtil.serialize(raw)
        }
        return parse(name, rawStr)
    }

    /**
     * Resolve a concrete message descriptor from a compiled FileDescriptor.
     *
     * Falls back to the first declared message type when [name] is null so
     * single-message schemas (the common case) never require an explicit hint.
     */
    private fun selectMessageDescriptor(
        fileDescriptor: Descriptors.FileDescriptor,
        name: String?
    ): Descriptors.Descriptor? {
        if (fileDescriptor.messageTypes.isEmpty()) return null
        if (name == null) return fileDescriptor.messageTypes[0]

        fileDescriptor.messageTypes.firstOrNull { it.fullName == name }?.let { return it }
        fileDescriptor.messageTypes.firstOrNull { it.name == name }?.let { return it }
        return findNestedMessage(fileDescriptor.messageTypes, name)
    }

    private fun findNestedMessage(
        messages: List<Descriptors.Descriptor>,
        targetName: String
    ): Descriptors.Descriptor? {
        for (msg in messages) {
            if (msg.name == targetName || msg.fullName == targetName) return msg
            msg.nestedTypes?.let { nested ->
                findNestedMessage(nested, targetName)?.let { return it }
            }
        }
        return null
    }

    /**
     * Normalise the raw input so that blank/null/empty forms are treated
     * identically — an empty JSON object that compiles to an empty schema.
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }

    /**
     * Sentinel used as the [Schema.parsed] value for an empty Protobuf schema
     * so validators can short-circuit without any extra flag.
     */
    object EmptyProtobufDescriptor
}
