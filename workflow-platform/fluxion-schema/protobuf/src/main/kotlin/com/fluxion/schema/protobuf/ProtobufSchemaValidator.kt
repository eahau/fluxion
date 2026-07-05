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

/**
 * Protobuf Schema 校验器。
 *
 * 通过将数据转换为 [DynamicMessage] 来验证字段类型和结构是否匹配 Schema。
 * 支持两种输入格式：
 * - Map<String, Any?>：通过 JSON 中转后解析
 * - String：Protobuf JSON 格式，直接解析
 * - DynamicMessage：已是 Protobuf 对象，直接做结构校验
 *
 * ### 校验范围
 *
 * - 字段类型是否匹配（int32/string/bool/message 等）
 * - 嵌套 message 结构是否正确
 * - repeated 字段是否为列表
 *
 * 注意：Protobuf 3 不支持 required 字段校验，所有字段均为 optional。
 * 未知字段会被忽略（Protobuf 标准行为）。
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
            // 将数据转为 DynamicMessage，成功即表示校验通过
            val message = toDynamicMessage(data, descriptor)
            log.debug("Protobuf schema [{}] validation passed", descriptor.fullName)
            ValidationResult.ok()
        } catch (e: Exception) {
            log.warn("Protobuf validation failed for [{}]: {}", descriptor.fullName, e.message)
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
     * 将任意格式的数据转换为 DynamicMessage。
     */
    private fun toDynamicMessage(data: Any, descriptor: Descriptors.Descriptor): DynamicMessage {
        return when (data) {
            is DynamicMessage -> {
                // 已是 DynamicMessage，验证类型是否匹配
                if (data.descriptorForType.fullName != descriptor.fullName) {
                    throw IllegalArgumentException(
                        "DynamicMessage type mismatch: expected ${descriptor.fullName}, " +
                        "got ${data.descriptorForType.fullName}"
                    )
                }
                data
            }

            is String -> {
                // Protobuf JSON 格式
                val builder = DynamicMessage.newBuilder(descriptor)
                JsonFormat.parser().merge(data, builder)
                builder.build()
            }

            else -> {
                // Map / POJO → JSON → DynamicMessage
                val json = com.fluxion.schema.util.JsonUtil.serialize(data)
                val builder = DynamicMessage.newBuilder(descriptor)
                JsonFormat.parser().merge(json, builder)
                builder.build()
            }
        }
    }

    /**
     * 尝试从异常消息中提取字段路径。
     */
    private fun extractPathFromException(e: Exception): String {
        val msg = e.message ?: return ""
        // 匹配 "field: xxx" 或类似模式
        val fieldMatch = Regex("""field\s+['"]?([\w.]+)['"]?""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch != null) return fieldMatch.groupValues[1]
        // 匹配 "xxx field"
        val fieldMatch2 = Regex("""([\w.]+)\s+field""", RegexOption.IGNORE_CASE).find(msg)
        if (fieldMatch2 != null) return fieldMatch2.groupValues[1]
        return ""
    }
}
