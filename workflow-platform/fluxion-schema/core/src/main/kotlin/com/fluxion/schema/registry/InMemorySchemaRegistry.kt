package com.fluxion.schema.registry

import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.model.Schema
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存中的 Schema 注册表实现。
 *
 * 支持：
 * - 幂等注册（同名覆盖，保证最新版本始终生效）
 * - 注销（Schema 删除时从注册表移除）
 * - 线程安全（ConcurrentHashMap）
 * - 版本追踪（保留每个 Schema 的最新 version）
 */
class InMemorySchemaRegistry : SchemaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    /** schemaName → Schema */
    private val schemas = ConcurrentHashMap<String, Schema>()

    /** schemaName → 最新版本号（用于增量更新判断） */
    private val versions = ConcurrentHashMap<String, Long>()

    /**
     * 注册或更新 Schema（幂等：同名覆盖）。
     *
     * @param schema Schema 对象（必须包含 name）
     */
    fun register(schema: Schema) {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")
        schemas[name] = schema
        log.debug("Registered schema: name={}, format={}", name, schema.format)
    }

    /**
     * 注册 Schema 并关联版本号。
     *
     * 仅当 [version] > 已记录版本时才覆盖（防止乱序推送导致回退）。
     *
     * @param schema  Schema 对象
     * @param version 版本号，0 表示不追踪版本
     * @return true 表示实际注册/更新了；false 表示因版本过旧被跳过
     */
    fun register(schema: Schema, version: Long): Boolean {
        val name = schema.name
            ?: throw IllegalArgumentException("Schema name is required for registration")

        if (version > 0) {
            val currentVersion = versions[name] ?: 0L
            if (version <= currentVersion) {
                log.debug("Skipped outdated schema update: name={}, incoming={}, current={}",
                    name, version, currentVersion)
                return false
            }
            versions[name] = version
        }

        schemas[name] = schema
        log.info("Registered schema: name={}, format={}, version={}", name, schema.format, version)
        return true
    }

    /**
     * 注销指定 Schema。
     *
     * @param name Schema 名称
     * @return true 表示已移除；false 表示不存在
     */
    fun unregister(name: String): Boolean {
        versions.remove(name)
        val removed = schemas.remove(name) != null
        if (removed) {
            log.info("Unregistered schema: name={}", name)
        } else {
            log.debug("Schema not found for unregister: name={}", name)
        }
        return removed
    }

    /**
     * 清空所有已注册 Schema。
     */
    fun clear() {
        schemas.clear()
        versions.clear()
    }

    /**
     * 获取当前已注册 Schema 数量。
     */
    fun size(): Int = schemas.size

    override fun get(name: String, version: Long?): Schema? {
        // 当前版本：忽略 version 参数，始终返回最新
        return schemas[name]
    }

    override fun list(): List<Schema> = schemas.values.toList()
}
