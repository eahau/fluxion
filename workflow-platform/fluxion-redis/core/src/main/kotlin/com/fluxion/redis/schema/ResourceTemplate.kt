package com.fluxion.redis.schema

import com.fluxion.core.redis.RedisKey
import com.fluxion.core.util.castOrNull
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 资源模板 — 对应 wf_resource_template 表
 */
data class ResourceTemplate(
    /** 资源名称（唯一标识） */
    val name: String,
    /** 资源类型 */
    val resourceType: ResourceType,
    /** 模板字符串，支持 ${variable} 占位符 */
    val template: String,
    /** 额外配置（如 TTL、超时时间等） */
    val config: Map<String, Any>? = null
) {
    enum class ResourceType {
        REDIS_KEY,
        MQ_TOPIC,
        HTTP_ENDPOINT
    }

    /**
     * 渲染模板，将 ${variable} 替换为实际值
     */
    fun render(variables: Map<String, String>): String {
        var result = template
        for ((k, v) in variables) {
            result = result.replace("\${$k}", v)
        }
        return result
    }

    /**
     * 获取配置项（带默认值）
     */
    inline fun <reified T> getConfig(key: String, defaultValue: T): T {
        if (config == null || !config.containsKey(key)) return defaultValue
        return config[key].castOrNull<T>() ?: defaultValue
    }

    /** 获取 TTL（秒），默认 0 表示无过期 */
    fun getTtlSeconds(): Long = getConfig("ttlSeconds", 0L)

    /** 获取超时时间（毫秒），默认 5000ms */
    fun getTimeoutMs(): Int = getConfig("timeoutMs", 5000)
}

/**
 * 资源模板注册中心 — 管理 wf_resource_template 表中的所有资源定义
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
        templates[name] ?: throw IllegalArgumentException("Resource template not found: $name")

    fun renderRedisKey(templateName: String, variables: Map<String, String>): String {
        return renderRedisKey(templateName, null, "", variables)
    }

    /**
     * 渲染 REDIS_KEY 模板，并按 {appName:domain}:businessKey 规范包装。
     *
     * 模板本身通常只描述业务键部分；appName/domain 由调用方从工作流定义/执行元信息提供。
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
