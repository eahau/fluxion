package com.fluxion.admin.crypto

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Component

/**
 * JPA attribute converter that transparently encrypts / decrypts **known sensitive keys**
 * inside a `Map<String, Any?>` JSON column before it reaches the DB and after it is read back.
 *
 * Strategy: keep JSON fully human-readable (so ad-hoc DB audits can still see structure,
 * usernames, hostnames, pool sizes, ...) but wrap only values whose key matches one of the
 * [SENSITIVE_KEYS] glob patterns.  [CryptService.encrypt] / `decrypt` is idempotent so values
 * that are already `ENC(...)` wrapped pass through untouched when re-read/rewritten.
 *
 * Used on [com.fluxion.admin.entity.AppResource.configJson] and
 * [com.fluxion.admin.entity.SandboxInstance.connectionConfig] (the latter as a safety net
 * so any redis-password embedded in a sandbox record is also encrypted at rest).
 */
@Converter
@Component
class EncryptedJsonMapConverter(
    private val objectMapper: ObjectMapper,
    private val cryptService: CryptService
) : AttributeConverter<MutableMap<String, Any?>, String> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapType = object : TypeReference<MutableMap<String, Any?>>() {}

    override fun convertToDatabaseColumn(attribute: MutableMap<String, Any?>?): String {
        if (attribute.isNullOrEmpty()) return EMPTY_JSON_OBJECT
        val encrypted = deepWalkAndTransform(attribute, transform = { key, value ->
            if (value is String && SENSITIVE_KEYS.any { it.matches(key.lowercase()) }) {
                cryptService.encrypt(value) ?: value
            } else {
                value
            }
        })
        return runCatching { objectMapper.writeValueAsString(encrypted) }
            .getOrElse { ex ->
                log.error(ex) { "Failed to serialize resource config JSON, falling back to empty object" }
                EMPTY_JSON_OBJECT
            }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableMap<String, Any?> {
        if (dbData.isNullOrBlank()) return mutableMapOf()
        val parsed = runCatching { objectMapper.readValue(dbData, mapType) }
            .getOrElse { ex ->
                log.error(ex) { "Failed to parse JSON config column, returning empty map; raw=${dbData.take(200)}" }
                return mutableMapOf()
            }
        return deepWalkAndTransform(parsed, transform = { _, value ->
            if (value is String) cryptService.decrypt(value) ?: value else value
        }) as MutableMap<String, Any?>
    }

    // ── helpers ────────────────────────────────────────────────────

    private fun deepWalkAndTransform(
        root: Any?,
        transform: (String, Any?) -> Any?
    ): Any? = when (root) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val m = root as Map<String, Any?>
            m.mapValues { (k, v) ->
                val leafTransformed = transform(k, v)
                if (leafTransformed === v) deepWalkAndTransform(v, transform) else leafTransformed
            }
        }
        is List<*> -> root.map { deepWalkAndTransform(it, transform) }
        is Set<*>  -> root.map { deepWalkAndTransform(it, transform) }.toSet()
        else       -> root
    }

    companion object {
        private const val EMPTY_JSON_OBJECT = "{}"

        /** Case-insensitive glob patterns — a key that contains any of these qualifies as sensitive. */
        private val SENSITIVE_KEYS: List<Glob> = listOf(
            Glob("*password*"), Glob("*passwd*"), Glob("*secret*"), Glob("*token*"),
            Glob("*sasljaas*"), Glob("*privatekey*"), Glob("*private_key*"),
            Glob("*apikey*"), Glob("*api_key*"), Glob("*accesskey*"), Glob("*access_key*"),
            Glob("*clientsecret*"), Glob("*client_secret*"), Glob("*credentials*")
        )
    }

    private class Glob(private val pattern: String) {
        private val regex = Regex(
            pattern
                .replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*")
        )
        fun matches(input: String): Boolean = regex.matches(input)
    }
}
