package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaDataProvider
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import org.slf4j.LoggerFactory

/**
 * Protobuf 数据访问提供者。
 *
 * 支持两种数据对象类型：
 * - [DynamicMessage]：通过 `getField(FieldDescriptor)` 访问字段
 * - `Map<String, Any?>`：作为 fallback，使用标准 Map 访问
 *
 * ### 使用场景
 *
 * 当工作流入参经过 Protobuf 协议解码后产生 `DynamicMessage` 对象时，
 * 此 provider 使得 NodeInput 能够透明地通过 `inputField("fieldName")` 访问字段，
 * 无需调用方感知底层数据类型。
 *
 * ```kotlin
 * // 在 WorkflowFunction 中
 * val name = input.inputField("name")  // 自动使用 DynamicMessage.getField()
 * ```
 */
class ProtobufSchemaDataProvider : SchemaDataProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getField(data: Any, field: String): Any? {
        return when (data) {
            is DynamicMessage -> {
                val descriptor = data.descriptorForType
                val fieldDescriptor = descriptor.findFieldByName(field)
                if (fieldDescriptor != null) {
                    data.getField(fieldDescriptor)
                } else {
                    log.debug(
                        "Protobuf field [{}] not found in message type [{}]",
                        field, descriptor.fullName
                    )
                    null
                }
            }

            is Map<*, *> -> data[field]
            else -> null
        }
    }

    override fun hasField(data: Any, field: String): Boolean {
        return when (data) {
            is DynamicMessage -> {
                data.descriptorForType.findFieldByName(field) != null
            }

            is Map<*, *> -> data.containsKey(field)
            else -> false
        }
    }

    override fun toMap(data: Any): Map<String, Any?> {
        return when (data) {
            is DynamicMessage -> dynamicMessageToMap(data)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                data as Map<String, Any?>
            }

            else -> emptyMap()
        }
    }

    override fun getFieldNames(data: Any): Set<String> {
        return when (data) {
            is DynamicMessage -> {
                data.descriptorForType.fields.map { it.name }.toSet()
            }

            is Map<*, *> -> data.keys.filterIsInstance<String>().toSet()
            else -> emptySet()
        }
    }

    /**
     * 将 DynamicMessage 递归转换为 Map。
     *
     * - 嵌套 Message → 递归转为 Map
     * - Repeated 字段 → 转为 List
     * - 基本类型 → 直接使用 Java 值
     */
    private fun dynamicMessageToMap(message: DynamicMessage): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        for (fieldDescriptor in message.descriptorForType.fields) {
            val value = message.getField(fieldDescriptor)
            result[fieldDescriptor.name] = convertProtobufValue(value, fieldDescriptor)
        }
        return result
    }

    private fun convertProtobufValue(value: Any?, fieldDescriptor: Descriptors.FieldDescriptor): Any? {
        if (value == null) return null
        return when {
            fieldDescriptor.isRepeated -> {
                @Suppress("UNCHECKED_CAST")
                (value as List<Any?>).map { convertSingleValue(it, fieldDescriptor) }
            }

            else -> convertSingleValue(value, fieldDescriptor)
        }
    }

    private fun convertSingleValue(value: Any?, fieldDescriptor: Descriptors.FieldDescriptor): Any? {
        return when (value) {
            is DynamicMessage -> dynamicMessageToMap(value)
            is com.google.protobuf.ByteString -> value.toByteArray()
            else -> value
        }
    }
}
