package com.fluxion.core.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * Shared JSON utilities backed by a single configured Jackson
 * [ObjectMapper] instance.
 *
 * Centralised here so that:
 *  - The engine serialises consistently across admin, runtime, and
 *    persistence layers (no drifting per-module ObjectMapper configs).
 *  - Kotlin data classes and default parameters work out of the box
 *    via `KotlinFeature.NullIsSameAsDefault`.
 *  - Unknown properties are ignored (forwards-compatible with newer
 *    admin payloads running on older runtimes).
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

    /** Shared immutable mapper; mutating the returned instance is UB. */
    @JvmStatic
    fun getMapper(): ObjectMapper = SHARED_MAPPER

    /** Fresh mapper with identical config; for callers that need to tweak settings. */
    @JvmStatic
    fun createCustomMapper(): ObjectMapper = createDefaultMapper()

    /** Serialise `source` to a compact JSON string. */
    @JvmStatic
    fun serialize(source: Any?): String = try {
        SHARED_MAPPER.writeValueAsString(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize failed: ${ex.message}", ex)
    }

    /** Serialise `source` to a pretty-printed JSON string (dev/debug only). */
    @JvmStatic
    fun serializePretty(source: Any?): String = try {
        SHARED_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize pretty failed: ${ex.message}", ex)
    }

    /** Serialise `source` to a UTF-8 byte array. */
    @JvmStatic
    fun serializeToBytes(source: Any?): ByteArray = try {
        SHARED_MAPPER.writeValueAsBytes(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize to bytes failed: ${ex.message}", ex)
    }

    /** Deserialise a JSON string into a concrete `Class<T>`. */
    @JvmStatic
    fun <T> deserialize(json: String, clazz: Class<T>): T = try {
        SHARED_MAPPER.readValue(json, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize failed: ${ex.message}", ex)
    }

    /** Deserialise using a Jackson [TypeReference] for generic shapes. */
    @JvmStatic
    fun <T> deserialize(json: String, typeReference: TypeReference<T>): T = try {
        SHARED_MAPPER.readValue(json, typeReference)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize with type reference failed: ${ex.message}", ex)
    }

    /** Deserialise from a UTF-8 byte array into a concrete `Class<T>`. */
    @JvmStatic
    fun <T> deserialize(bytes: ByteArray, clazz: Class<T>): T = try {
        SHARED_MAPPER.readValue(bytes, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize from bytes failed: ${ex.message}", ex)
    }

    /** Parse a JSON object into a `Map<String, Any>`. */
    @JvmStatic
    fun toMap(json: String): Map<String, Any> =
        deserialize(json, object : TypeReference<Map<String, Any>>() {})

    /** Convert any bean-ish `source` into a `Map<String, Any>` via convertValue. */
    @JvmStatic
    fun toMap(source: Any): Map<String, Any> =
        SHARED_MAPPER.convertValue(source, object : TypeReference<Map<String, Any>>() {})

    /** Parse a JSON array into a `List<Any>`. */
    @JvmStatic
    fun toList(json: String): List<Any> =
        deserialize(json, object : TypeReference<List<Any>>() {})

    /**
     * True iff `json` parses as valid JSON (not blank, not a partial
     * document, no unterminated strings / brackets).  Used by the
     * schema / admin validators to short-circuit without throwing.
     */
    @JvmStatic
    fun isValidJson(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            SHARED_MAPPER.readTree(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Round-trip deep-copy via serialise → deserialise.
     *
     * Cheaper than reflection-based copiers for values that already
     * have clean JSON mappings; also the canonical way to "detach" a
     * bean from lazy-loading proxies before handing it across module
     * boundaries.
     */
    @JvmStatic
    fun <T> deepCopy(source: T, clazz: Class<T>): T {
        val json = serialize(source)
        return deserialize(json, clazz)
    }

    /** Typed `convertValue` using a target Class. */
    @JvmStatic
    fun <T> convertValue(source: Any?, clazz: Class<T>): T = try {
        SHARED_MAPPER.convertValue(source, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON convertValue failed: ${ex.message}", ex)
    }

    /** Typed `convertValue` using a `TypeReference` for generics. */
    @JvmStatic
    fun <T> convertValue(source: Any?, typeReference: TypeReference<T>): T = try {
        SHARED_MAPPER.convertValue(source, typeReference)
    } catch (ex: Exception) {
        throw RuntimeException("JSON convertValue with type reference failed: ${ex.message}", ex)
    }

    /** Convert a value to a Jackson tree (`JsonNode`) for inspection. */
    @JvmStatic
    fun valueToTree(value: Any?): JsonNode = try {
        SHARED_MAPPER.valueToTree(value)
    } catch (ex: Exception) {
        throw RuntimeException("JSON valueToTree failed: ${ex.message}", ex)
    }

    /** Deserialise a JSON array into a typed `List<T>` (element class supplied). */
    @JvmStatic
    fun <T> deserializeList(json: String, elementClass: Class<T>): List<T> = try {
        SHARED_MAPPER.readValue(
            json,
            SHARED_MAPPER.typeFactory.constructCollectionType(List::class.java, elementClass)
        )
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize list failed: ${ex.message}", ex)
    }

    /** Deserialise a JSON array into a typed `Set<T>` (element class supplied). */
    @JvmStatic
    fun <T> deserializeSet(json: String, elementClass: Class<T>): Set<T> = try {
        SHARED_MAPPER.readValue(
            json,
            SHARED_MAPPER.typeFactory.constructCollectionType(Set::class.java, elementClass)
        )
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize set failed: ${ex.message}", ex)
    }

    /**
     * `convertValue` that returns null instead of throwing on failure,
     * and additionally passes through `null` source as `null`.
     *
     * Convenience wrapper for decorator / function config maps where a
     * bad shape should degrade gracefully rather than blow up the
     * whole workflow.
     */
    inline fun <reified T> convertValueOrNull(source: Any?): T? = try {
        source?.let { convertValue(it, object : TypeReference<T>() {}) }
    } catch (_: Exception) {
        null
    }

    /** Reified `deserialize` so Kotlin callers can skip the Class arg. */
    inline fun <reified T> deserialize(json: String): T = deserialize(json, T::class.java)
}
