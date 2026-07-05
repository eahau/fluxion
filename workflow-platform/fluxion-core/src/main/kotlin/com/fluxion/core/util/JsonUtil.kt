package com.fluxion.core.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * JSON 序列化工具类 — workflow-core 层统一序列化入口
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

    /** 获取共享的 ObjectMapper 实例（只读配置） */
    @JvmStatic
    fun getMapper(): ObjectMapper = SHARED_MAPPER

    /** 创建自定义配置的 ObjectMapper 实例 */
    @JvmStatic
    fun createCustomMapper(): ObjectMapper = createDefaultMapper()

    // ─── 序列化方法 ──────────────────────────────────────────────

    /** 序列化为 JSON 字符串 */
    @JvmStatic
    fun serialize(source: Any?): String = try {
        SHARED_MAPPER.writeValueAsString(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize failed: ${ex.message}", ex)
    }

    /** 序列化为格式化的 JSON 字符串 */
    @JvmStatic
    fun serializePretty(source: Any?): String = try {
        SHARED_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize pretty failed: ${ex.message}", ex)
    }

    /** 序列化为 JSON 字节数组 */
    @JvmStatic
    fun serializeToBytes(source: Any?): ByteArray = try {
        SHARED_MAPPER.writeValueAsBytes(source)
    } catch (ex: Exception) {
        throw RuntimeException("JSON serialize to bytes failed: ${ex.message}", ex)
    }

    // ─── 反序列化方法 ────────────────────────────────────────────

    /** 从 JSON 字符串反序列化为指定类型 */
    @JvmStatic
    fun <T> deserialize(json: String, clazz: Class<T>): T = try {
        SHARED_MAPPER.readValue(json, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize failed: ${ex.message}", ex)
    }

    /** 从 JSON 字符串反序列化（通过 TypeReference 支持泛型） */
    @JvmStatic
    fun <T> deserialize(json: String, typeReference: TypeReference<T>): T = try {
        SHARED_MAPPER.readValue(json, typeReference)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize with type reference failed: ${ex.message}", ex)
    }

    /** 从字节数组反序列化为指定类型 */
    @JvmStatic
    fun <T> deserialize(bytes: ByteArray, clazz: Class<T>): T = try {
        SHARED_MAPPER.readValue(bytes, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize from bytes failed: ${ex.message}", ex)
    }

    /** JSON 字符串 → Map（通用类型） */
    @JvmStatic
    fun toMap(json: String): Map<String, Any> =
        deserialize(json, object : TypeReference<Map<String, Any>>() {})

    /** 任意对象 → Map（通过 convertValue） */
    @JvmStatic
    fun toMap(source: Any): Map<String, Any> =
        SHARED_MAPPER.convertValue(source, object : TypeReference<Map<String, Any>>() {})

    /** JSON 字符串 → List（通用类型） */
    @JvmStatic
    fun toList(json: String): List<Any> =
        deserialize(json, object : TypeReference<List<Any>>() {})

    // ─── 工具方法 ────────────────────────────────────────────────

    /** 判断字符串是否为合法 JSON */
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

    /** 深拷贝（通过 JSON 序列化/反序列化） */
    @JvmStatic
    fun <T> deepCopy(source: T, clazz: Class<T>): T {
        val json = serialize(source)
        return deserialize(json, clazz)
    }

    // ─── 类型转换 ────────────────────────────────────────────────

    /** 对象类型转换（类似 BeanUtils，但支持嵌套泛型） */
    @JvmStatic
    fun <T> convertValue(source: Any?, clazz: Class<T>): T = try {
        SHARED_MAPPER.convertValue(source, clazz)
    } catch (ex: Exception) {
        throw RuntimeException("JSON convertValue failed: ${ex.message}", ex)
    }

    /** 对象类型转换（通过 TypeReference 支持泛型） */
    @JvmStatic
    fun <T> convertValue(source: Any?, typeReference: TypeReference<T>): T = try {
        SHARED_MAPPER.convertValue(source, typeReference)
    } catch (ex: Exception) {
        throw RuntimeException("JSON convertValue with type reference failed: ${ex.message}", ex)
    }

    /** 将值转换为 JsonNode 树结构 */
    @JvmStatic
    fun valueToTree(value: Any?): JsonNode = try {
        SHARED_MAPPER.valueToTree(value)
    } catch (ex: Exception) {
        throw RuntimeException("JSON valueToTree failed: ${ex.message}", ex)
    }

    // ─── 集合反序列化 ─────────────────────────────────────────────

    /** 反序列化为 List（元素类型安全） */
    @JvmStatic
    fun <T> deserializeList(json: String, elementClass: Class<T>): List<T> = try {
        SHARED_MAPPER.readValue(
            json,
            SHARED_MAPPER.typeFactory.constructCollectionType(List::class.java, elementClass)
        )
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize list failed: ${ex.message}", ex)
    }

    /** 反序列化为 Set（元素类型安全） */
    @JvmStatic
    fun <T> deserializeSet(json: String, elementClass: Class<T>): Set<T> = try {
        SHARED_MAPPER.readValue(
            json,
            SHARED_MAPPER.typeFactory.constructCollectionType(Set::class.java, elementClass)
        )
    } catch (ex: Exception) {
        throw RuntimeException("JSON deserialize set failed: ${ex.message}", ex)
    }

    // ─── Kotlin 扩展 ─────────────────────────────────────────────

    /**
     * 将任意对象按 reified 类型转换，转换失败或 source 为 null 时返回 null。
     * 通过 TypeReference 支持嵌套泛型（如 List<Map<String, Any>>），避免 unchecked cast。
     */
    inline fun <reified T> convertValueOrNull(source: Any?): T? = try {
        source?.let { convertValue(it, object : TypeReference<T>() {}) }
    } catch (_: Exception) {
        null
    }

    /** 反序列化为 reified 类型（Kotlin 内联扩展） */
    inline fun <reified T> deserialize(json: String): T = deserialize(json, T::class.java)
}
