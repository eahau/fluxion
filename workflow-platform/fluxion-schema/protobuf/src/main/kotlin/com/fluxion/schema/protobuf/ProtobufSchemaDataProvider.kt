/**
 * Protobuf-backed [SchemaDataProvider] enabling transparent field access on
 * [DynamicMessage] payloads via the generic `inputField("name")` workflow API.
 *
 * Also accepts plain `Map<String, *>` as a fallback so mixed workloads that
 * sometimes receive JSON-decoded maps and sometimes DynamicMessages behave
 * consistently. Nested messages, repeated fields and `ByteString` values are
 * recursively converted to Kotlin collections when [toMap] is called.
 */
package com.fluxion.schema.protobuf

import com.fluxion.schema.api.SchemaDataProvider
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * Exposes Protobuf DynamicMessage fields through the generic data SPI.
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
                    // DEBUG level because missing fields often represent
                    // legitimate optional-value cases on proto3 messages.
                    log.debug { "Protobuf field [$field] not found in message type [${descriptor.fullName}]" }
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
     * Recursively flatten a [DynamicMessage] into a Kotlin LinkedHashMap
     * (preserves field declaration order) while translating Protobuf-specific
     * value types to standard JVM equivalents.
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
