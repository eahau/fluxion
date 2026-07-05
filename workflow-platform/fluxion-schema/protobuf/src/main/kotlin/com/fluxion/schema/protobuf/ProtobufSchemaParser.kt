package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaParser
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.util.JsonFormat
import org.slf4j.LoggerFactory

/**
 * Protobuf Schema 解析器。
 *
 * 接受 Protobuf FileDescriptorProto 的 JSON 表示（由 `protoc --descriptor_set_out --json_format` 生成），
 * 将其解析为 [Descriptors.Descriptor]（Message 级描述符）并封装在统一的 [Schema] 容器中。
 *
 * ### Message 选择规则
 *
 * FileDescriptorProto 可能包含多个 messageType，选择优先级：
 * 1. 如果指定了 name，优先匹配同名 message
 * 2. 否则选择第一个 messageType
 * 3. 如果没有 messageType，抛出异常
 *
 * 空 Schema（空白字符串 / "{}" / "null"）会被规范化，
 * 与 JSON Schema 的空 Schema 行为保持一致：校验时直接通过。
 */
class ProtobufSchemaParser : SchemaParser {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun parse(name: String?, raw: String): Schema {
        val normalized = normalizeRaw(raw)
        return try {
            // 1. 解析 FileDescriptorProto
            val fileProtoBuilder = DescriptorProtos.FileDescriptorProto.newBuilder()
            JsonFormat.parser().merge(normalized, fileProtoBuilder)
            val fileProto = fileProtoBuilder.build()

            // 空 Schema：没有 messageType，返回空描述符
            if (fileProto.messageTypeCount == 0) {
                return Schema(
                    name = name,
                    format = SchemaFormat.PROTOBUF,
                    raw = normalized,
                    parsed = EmptyProtobufDescriptor
                )
            }

            // 2. 构建 FileDescriptor（无外部依赖）
            val fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto, emptyArray())

            // 3. 选择目标 MessageDescriptor
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
            log.error("Failed to parse Protobuf schema: ${e.message}", e)
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
     * 选择目标 MessageDescriptor。
     *
     * 优先级：
     * 1. 精确匹配全名
     * 2. 匹配短名
     * 3. 返回第一个 messageType
     */
    private fun selectMessageDescriptor(
        fileDescriptor: Descriptors.FileDescriptor,
        name: String?
    ): Descriptors.Descriptor? {
        if (fileDescriptor.messageTypes.isEmpty()) return null
        if (name == null) return fileDescriptor.messageTypes[0]

        // 精确匹配全名
        fileDescriptor.messageTypes.firstOrNull { it.fullName == name }?.let { return it }
        // 匹配短名
        fileDescriptor.messageTypes.firstOrNull { it.name == name }?.let { return it }
        // 嵌套匹配（简单实现：递归查找所有嵌套 message）
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
     * 规范化原始输入：将 null / 空字符串 / 空对象统一视为空 Schema `"{}"`，
     * 与 JSON Schema 的 empty schema 行为保持一致。
     */
    private fun normalizeRaw(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isBlank() || trimmed == "null") "{}" else trimmed
    }

    /**
     * 空 Protobuf 描述符标记对象。
     * 用于表示空 Schema，校验时直接通过。
     */
    object EmptyProtobufDescriptor
}
