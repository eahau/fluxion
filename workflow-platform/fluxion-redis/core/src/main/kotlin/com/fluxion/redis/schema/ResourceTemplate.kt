package com.fluxion.redis.schema

import com.fluxion.core.redis.RedisKey
import com.fluxion.core.util.castOrNull
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Named template for a resource address (Redis key, MQ topic, HTTP endpoint, ...)
 * that is rendered per-invocation from workflow variables.
 *
 * Mirrors the logical `wf_resource_template` table; templates are typically loaded
 * from database seed data or from an external catalog and registered into
 * `ResourceTemplateRegistry` at startup.
 *
 * @property name         stable unique identifier used to reference the template from workflow nodes.
 * @property resourceType one of the supported resource categories in [ResourceType].
 * @property template     pattern string with `${variable}` placeholders that will be
 *                        substituted during `render(..)`.
 * @property config       optional free-form config map (TTL, timeout, retry policy, etc.).
 */
data class ResourceTemplate(
    val name: String,
    val resourceType: ResourceType,
    val template: String,
    val config: Map<String, Any>? = null
) {
    enum class ResourceType {
        REDIS_KEY,
        MQ_TOPIC,
        HTTP_ENDPOINT
    }

    /** Substitute every `${key}` occurrence in [template] using the supplied variable map. */
    fun render(variables: Map<String, String>): String {
        var result = template
        for ((k, v) in variables) {
            result = result.replace("\${$k}", v)
        }
        return result
    }

    /**
     * Read a single config entry with a compile-time-inferred type and a caller-supplied
     * default when the key is absent or cannot be cast.
     */
    inline fun <reified T> getConfig(key: String, defaultValue: T): T {
        if (config == null || !config.containsKey(key)) return defaultValue
        return config[key].castOrNull<T>() ?: defaultValue
    }

    /** Convenience helper: Redis key TTL in seconds (0 = never expire). */
    fun getTtlSeconds(): Long = getConfig("ttlSeconds", 0L)

    /** Convenience helper: HTTP / lock timeout in milliseconds (default 5s). */
    fun getTimeoutMs(): Int = getConfig("timeoutMs", 5000)
}

/**
 * In-memory catalog of `ResourceTemplate` instances plus convenience render helpers.
 *
 * Concurrent safe (backed by `ConcurrentHashMap`). Designed for single-writer registration
 * during Spring context refresh and multi-reader lookup during hot workflow execution.
 */
class ResourceTemplateRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val templates = ConcurrentHashMap<String, ResourceTemplate>()

    fun register(template: ResourceTemplate) {
        templates[template.name] = template
        log.info { "Registered resource template: ${template.name} (type=${template.resourceType})" }
    }

    fun registerAll(templates: Iterable<ResourceTemplate>) {
        templates.forEach(::register)
    }

    fun get(name: String): ResourceTemplate? = templates[name]

    fun require(name: String): ResourceTemplate =
        templates[name] ?: throw IllegalArgumentException("Resource template not found: `$name")

    fun renderRedisKey(templateName: String, variables: Map<String, String>): String {
        return renderRedisKey(templateName, null, "", variables)
    }

    /**
     * Render a `REDIS_KEY` template and wrap the business key following the convention
     * `{appName:domain}:businessKey` so cluster hash-tags colocate related data.
     *
     * The caller supplies `appName` / `domain` from `ExecutionMeta`; the template itself
     * only expresses the business suffix (e.g. `user:${userId}:profile`).
     */
    fun renderRedisKey(
        templateName: String,
        appName: String?,
        domain: String,
        variables: Map<String, String>
    ): String {
        val template = require(templateName)
        require(template.resourceType == ResourceTemplate.ResourceType.REDIS_KEY) {
            "Template $templateName is not a REDIS_KEY type"
        }
        val rendered = template.render(variables)
        return RedisKey.wrapIfNeeded(appName, domain, rendered)
    }

    fun contains(name: String): Boolean = templates.containsKey(name)

    fun size(): Int = templates.size

    fun clear() {
        templates.clear()
        log.info { "Cleared all resource templates" }
    }
}
