package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory

/**
 * Protobuf Schema 编解码器。
 *
 * 支持二进制（Protobuf wire format）和 JSON 两种序列化格式。
 * 基于 [DynamicMessage] 实现，无需代码生成，支持运行时动态 Schema。
 *
 * ### 数据类型约定
 *
 * - **serialize 输入**：支持 `DynamicMessage`、`Map<String, Any?>`、JSON 字符串
 * - **deserialize 输出**：返回 `DynamicMessage`，可通过 [ProtobufSchemaDataProvider] 访问字段
 * - **toJson 输出**：Protobuf JSON 格式字符串（使用 protobuf-java-util 的 JsonFormat）
 * - **fromJson 输入**：Protobuf JSON 格式字符串，输出 `DynamicMessage`
 *
 * ### 使用示例
 *
 * ```kotlin
 * val codec = ProtobufSchemaCodec()
 * val bytes = codec.serialize(dataMap, schema)       // Map → 二进制
 * val message = codec.deserialize(bytes, schema)      // 二进制 → DynamicMessage
 * val json = codec.toJson(message, schema)            // DynamicMessage → JSON
 * ```
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

    // ─── 内部工具方法 ──────────────────────────────────────────────

    private fun getDescriptor(schema: Schema): Descriptors.Descriptor {
        require(schema.format == SchemaFormat.PROTOBUF) {
            "ProtobufSchemaCodec only supports PROTOBUF format, got ${schema.format}"
        }
        return schema.parsed as? Descriptors.Descriptor
            ?: throw IllegalArgumentException("Schema parsed object is not a Protobuf Descriptor")
    }

    /**
     * 将任意格式的数据转换为 DynamicMessage。
     *
     * 支持：
     * - DynamicMessage：直接返回（验证类型匹配）
     * - String：Protobuf JSON 格式，解析为 DynamicMessage
     * - 其他（Map / POJO）：先序列化为 JSON，再解析为 DynamicMessage
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
