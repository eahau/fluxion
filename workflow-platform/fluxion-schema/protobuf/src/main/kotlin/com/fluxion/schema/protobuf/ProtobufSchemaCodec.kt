/**
 * [SchemaCodec] implementation for the Protobuf wire format plus
 * Protobuf-style JSON (via `protobuf-java-util` [JsonFormat]).
 *
 * All conversions route through [DynamicMessage] so the codec works with
 * arbitrary runtime descriptors and never requires compiled message classes.
 * Accepted input shapes on the serialize path: [DynamicMessage] itself,
 * pre-formatted Protobuf JSON strings, or generic Map/POJO structures that
 * first get Jackson-serialized then merged via JsonFormat.parser.
 */
package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Binary + JSON serialization codec for schemas of format PROTOBUF.
 */
class ProtobufSchemaCodec : SchemaCodec {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun serialize(data: Any?, schema: Schema): ByteArray {
        if (data == null) return ByteArray(0)
        if (schema.isEmpty) return ByteArray(0)

        val descriptor = getDescriptor(schema)
        val message = toDynamicMessage(data, descriptor)
        return message.toByteArray()
    }

    override fun deserialize(bytes: ByteArray, schema: Schema): Any? {
        if (bytes.isEmpty()) return null
        if (schema.isEmpty) return null

        val descriptor = getDescriptor(schema)
        return DynamicMessage.parseFrom(descriptor, bytes)
    }

    override fun toJson(data: Any?, schema: Schema): String {
        if (data == null) return "{}"
        if (schema.isEmpty) return com.fluxion.schema.util.JsonUtil.serialize(data)

        val descriptor = getDescriptor(schema)
        val message = toDynamicMessage(data, descriptor)
        return JsonFormat.printer().print(message)
    }

    override fun fromJson(json: String, schema: Schema): Any? {
        if (json.isBlank() || json == "{}" || json == "null") return null
        if (schema.isEmpty) return com.fluxion.schema.util.JsonUtil.deserialize(json, Any::class.java)

        val descriptor = getDescriptor(schema)
        val builder = DynamicMessage.newBuilder(descriptor)
        JsonFormat.parser().merge(json, builder)
        return builder.build()
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun getDescriptor(schema: Schema): Descriptors.Descriptor {
        require(schema.format == SchemaFormat.PROTOBUF) {
            "ProtobufSchemaCodec only supports PROTOBUF format, got ${schema.format}"
        }
        return schema.parsed as? Descriptors.Descriptor
            ?: throw IllegalArgumentException("Schema parsed object is not a Protobuf Descriptor")
    }

    /**
     * Coerce an arbitrary value into a [DynamicMessage] conforming to
     * [descriptor].
     *
     * * DynamicMessage → type-check then reuse.
     * * String        → assume Protobuf JSON, merge with JsonFormat.parser.
     * * Otherwise     → Jackson-serialize then JSON-merge.
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
                val json = com.fluxion.schema.util.JsonUtil.serialize(data)
                val builder = DynamicMessage.newBuilder(descriptor)
                JsonFormat.parser().merge(json, builder)
                builder.build()
            }
        }
    }
}
