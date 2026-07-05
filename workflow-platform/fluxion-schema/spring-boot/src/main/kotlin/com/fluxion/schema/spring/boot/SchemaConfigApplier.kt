package com.fluxion.schema.spring.boot

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.SchemaChangeListener
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.adapter.spi.config.SchemaConfigSubscriber
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.model.SchemaFormat
import com.fluxion.schema.registry.InMemorySchemaRegistry
import org.slf4j.LoggerFactory

/**
 * Worker 侧 Schema 配置应用器。
 *
 * 接收配置中心（HTTP / Apollo / Nacos）推送的 Schema 快照，
 * 将 Schema 解析并注册到本地 [InMemorySchemaRegistry]，实现 Schema 热更新。
 *
 * 全链路：
 * ```text
 * Admin WfSchemaService → SchemaConfigPublisher → 配置中心 →
 * SchemaConfigSubscriber → SchemaConfigApplier → InMemorySchemaRegistry
 * ```
 *
 * 支持多格式 Schema（JSON Schema / Protobuf / Avro），
 * 根据 [SchemaConfigSnapshot.schemaFormat] 自动选择对应的 [SchemaManager] 解析器。
 */
class SchemaConfigApplier(
    private val subscriber: SchemaConfigSubscriber,
    private val registry: InMemorySchemaRegistry,
    private val schemaManager: SchemaManager,
    private val scope: String? = null
) : SchemaChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 加载全量 Schema 快照并注册监听器（应用启动时调用）。
     */
    fun init() {
        val snapshots = subscriber.loadAll()
        for (snap in snapshots) {
            applySnapshot(snap, ChangeType.PUBLISH)
        }
        log.info("SchemaConfigApplier initialized with ${snapshots.size} schemas")
        subscriber.watch(this)
    }

    /**
     * 响应 Schema 配置变更事件。
     */
    override fun onChange(key: String, snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        try {
            applySnapshot(snapshot, changeType)
        } catch (e: Exception) {
            log.error("Failed to apply schema config change: name=${snapshot.schemaName}, type=$changeType", e)
            // 降级：不影响工作流执行，Schema 保持上一个版本
        }
    }

    private fun applySnapshot(snapshot: SchemaConfigSnapshot, changeType: ChangeType) {
        val name = snapshot.schemaName

        // scope 过滤：PRIVATE Schema 仅对匹配的 Worker 可见
        if (snapshot.scope == "PRIVATE" && scope != null && snapshot.appGroup != scope) {
            log.debug("Skipped private schema [{}] (appGroup={} not matching worker scope={})",
                name, snapshot.appGroup, scope)
            return
        }

        when (changeType) {
            ChangeType.REMOVE -> {
                registry.unregister(name)
                log.info("Removed schema [{}] from registry", name)
            }
            else -> {
                if (!snapshot.enabled || snapshot.schemaJson.isNullOrBlank()) {
                    log.warn("Skipped disabled/empty schema [{}]", name)
                    return
                }

                val schemaJson: String = snapshot.schemaJson!!
                val format = parseFormat(snapshot.schemaFormat)
                val schema = schemaManager.parse(format, schemaJson)

                val registered = if (snapshot.version > 0) {
                    registry.register(schema, snapshot.version)
                } else {
                    registry.register(schema)
                    true
                }

                if (registered) {
                    log.info("Applied schema [{}] format={} version={} (changeType={})",
                        name, snapshot.schemaFormat, snapshot.version, changeType)
                }
            }
        }
    }

    private fun parseFormat(format: String): SchemaFormat {
        return try {
            SchemaFormat.fromCode(format)
        } catch (e: Exception) {
            log.warn("Unknown schema format [{}], falling back to JSON_SCHEMA", format)
            SchemaFormat.JSON_SCHEMA
        }
    }
}
