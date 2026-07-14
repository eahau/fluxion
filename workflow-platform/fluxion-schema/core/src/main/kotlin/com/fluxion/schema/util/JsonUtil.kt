/**
 * Shared JSON helpers internal to the `fluxion-schema` module family.
 *
 * Centralises a Jackson [ObjectMapper] pre-configured with:
 * * KotlinModule with `NullIsSameAsDefault` enabled so data classes treat
 *   absent fields the same as explicit `null` values.
 * * `FAIL_ON_UNKNOWN_PROPERTIES = false` — schemas evolve independently from
 *   the parsing code, so unknown fields must never blow up deserialization.
 * * `WRITE_DATES_AS_TIMESTAMPS = false` — ISO-8601 string dates interop
 *   better with external consumers and persisted snapshots.
 */
package com.fluxion.schema.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Internal utility facade for JSON operations needed by parsers/validators
 * and format-specific data providers.
 */
object JsonUtil {

    private val SHARED_MAPPER: ObjectMapper = createDefaultMapper()

    private fun createDefaultMapper(): ObjectMapper = ObjectMapper().apply {
        registerModule(
            KotlinModule.Builder()
                .enable(KotlinFeature.NullIsSameAsDefault)
                .build()
        )
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    @JvmStatic
    fun serialize(source: Any?): String = try {
        SHARED_MAPPER.writeValueAsString(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize failed: ${ex.message}", ex)
    }

    @JvmStatic
    fun valueToTree(value: Any?): JsonNode = try {
        SHARED_MAPPER.valueToTree(value)
    } catch (ex: Exception) {
        throw RuntimeException("JSON valueToTree failed: ${ex.message}", ex)
    }

    @JvmStatic
    fun <T> deserialize(json: String, clazz: Class<T>): T = try {
        SHARED_MAPPER.readValue(json, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize failed: ${ex.message}", ex)
    }

    @JvmStatic
    fun toMap(source: Any): Map<String, Any> =
        SHARED_MAPPER.convertValue(source, object : TypeReference<Map<String, Any>>() {})
}
