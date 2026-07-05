package com.fluxion.config.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigService
import com.ctrip.framework.apollo.enums.PropertyChangeType
import com.ctrip.framework.apollo.model.ConfigChangeEvent
import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.KeyedConfigChangeListener
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Apollo 多键配置订阅器 — 封装 Apollo namespace 获取、监听器注册和变更事件分发
 *
 * 本类为具体类（非 abstract），可直接实例化使用。
 * 封装以下公共逻辑：
 * - [Config] 实例获取（通过 namespace）
 * - 监听器注册的原子性保障（[AtomicBoolean]）
 * - `loadAll()` / `get(key)` 的 propertyNames 遍历模式
 * - `handleConfigChange()` 的变更事件迭代 + ChangeType 映射
 *
 * 校验与降级由 [AbstractKeyedConfigSubscriber.resolveAndGet] 统一保障。
 *
 * ## 使用方式
 *
 * ### 简单场景（直接实例化）
 * ```kotlin
 * ApolloKeyedConfigSubscriber(
 *     namespace = "workflow-definitions",
 *     mapper = { key, content -> WorkflowDefinitionSnapshot.of(key, content, 0, true) },
 *     removedMapper = { key -> WorkflowDefinitionSnapshot.removed(key) })
 * ```
 *
 * ### 复杂场景（需要引用子类 log 等实例字段）
 * 继承本类并 override [mapKeyToSnapshot] / [buildRemovedSnapshot]：
 * ```kotlin
 * class ApolloFunctionConfigSubscriber :
 *     ApolloKeyedConfigSubscriber<FunctionConfigSnapshot>(namespace = "workflow-functions") {
 *     override fun mapKeyToSnapshot(key: String, content: String) =
 *         SnapshotParser.parseFunctionSnapshot(content, key, log)
 *     override fun buildRemovedSnapshot(key: String) = FunctionConfigSnapshot.removed(key)
 * }
 * ```
 *
 * @param T               配置快照类型
 * @param namespace        Apollo namespace 名称
 * @param mapper           原始 JSON → 类型化快照的转换函数（可选，子类可 override [mapKeyToSnapshot] 替代）
 * @param removedMapper    key → 已删除标记快照的构建函数（可选，子类可 override [buildRemovedSnapshot] 替代）
 */
open class ApolloKeyedConfigSubscriber<T : Any>(
    private val namespace: String,
    private val mapper: ((key: String, content: String) -> T?)? = null,
    private val removedMapper: ((key: String) -> T)? = null
) : AbstractKeyedConfigSubscriber<T>() {

    private val config: Config by lazy { ConfigService.getConfig(namespace) }
    private val listenerAdded = AtomicBoolean(false)

    // ─── 双路径解析 ─────────────────────────────────────────────────

    /**
     * 将原始配置内容映射为类型化快照。
     *
     * 双路径解析：
     * - 若构造时提供了 [mapper] → 委托给 mapper
     * - 否则由子类 override 此方法提供自定义解析
     */
    override fun mapKeyToSnapshot(key: String, content: String): T? = mapper?.invoke(key, content)

    /**
     * 构建已删除的标记快照。
     *
     * 双路径：
     * - 若构造时提供了 [removedMapper] → 委托给 removedMapper
     * - 否则由子类 override 此方法
     */
    protected open fun buildRemovedSnapshot(key: String): T =
        removedMapper?.invoke(key)
            ?: throw UnsupportedOperationException(
                "buildRemovedSnapshot not implemented: provide removedMapper or override this method")

    // ─── SPI 实现（统一模式）──────────────────────────────────────

    override fun loadAll(): List<T> {
        val keys = config.propertyNames
        val result = keys.mapNotNull { key ->
            val json = config.getProperty(key, null)
            if (!json.isNullOrBlank()) resolveAndGet(key, json) else null
        }
        log.info("Loaded ${result.size} configs from Apollo namespace [$namespace]")
        return result
    }

    override fun get(key: String): T? {
        val json = config.getProperty(key, null)
        if (json.isNullOrBlank()) return null
        return resolveAndGet(key, json)
    }

    override fun watch(listener: KeyedConfigChangeListener<T>) {
        super.watch(listener)
        if (listenerAdded.compareAndSet(false, true)) {
            config.addChangeListener { event -> handleConfigChange(event) }
            log.info("Registered Apollo change listener on namespace [$namespace]")
        }
    }

    // ─── 内部方法 ─────────────────────────────────────────────────

    private fun handleConfigChange(event: ConfigChangeEvent) {
        for (key in event.changedKeys()) {
            val change = event.getChange(key)
            when (change.changeType) {
                PropertyChangeType.DELETED -> {
                    evictSnapshot(key)
                    notifyListeners(key, buildRemovedSnapshot(key), ChangeType.REMOVE)
                }
                PropertyChangeType.ADDED, PropertyChangeType.MODIFIED -> {
                    resolveAndGet(key, change.newValue ?: continue)?.let { snapshot ->
                        val type = if (change.changeType == PropertyChangeType.ADDED) ChangeType.PUBLISH else ChangeType.UPDATE
                        notifyListeners(key, snapshot, type)
                    }
                }
                else -> {}
            }
        }
    }
}
