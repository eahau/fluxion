package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.KeyedConfigChangeListener
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import com.fluxion.config.core.SnapshotParser
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nacos 多键配置订阅器 — 封装 Nacos 索引 + 独立 dataId 模式的公共逻辑
 *
 * 本类为具体类（非 abstract），可直接实例化使用。
 * 封装以下公共逻辑：
 * - [ConfigService] 依赖注入
 * - 虚拟线程 [listenerExecutor]
 * - 索引监听器注册的原子性保障（[AtomicBoolean]）
 * - `loadAll()` / `get(key)` / `loadIndex()` / `registerIndexListener()` 的完整实现
 * - `watch()` 的 super + compareAndSet 模式
 *
 * 校验与降级由 [AbstractKeyedConfigSubscriber.resolveAndGet] 统一保障。
 *
 * ## 使用方式
 *
 * ### 简单场景（索引变更时全量刷新）
 * 直接实例化本类，传入 [mapper] 函数即可：
 * ```kotlin
 * NacosKeyedConfigSubscriber(configService, "WORKFLOW", "workflow.definition.",
 *     "workflow.definition.__index__",
 *     mapper = { key, content -> WorkflowDefinitionSnapshot.of(key, content, 0, true) })
 * ```
 *
 * ### 复杂场景（索引变更时需 diff 对比 / 独立监听器）
 * 继承本类，override [handleIndexChange]：
 * ```kotlin
 * class NacosFunctionConfigSubscriber(configService: ConfigService) :
 *     NacosKeyedConfigSubscriber<FunctionConfigSnapshot>(...)
 * ```
 *
 * @param T 配置快照类型
 * @param configService  Nacos 配置服务
 * @param group          Nacos 分组名
 * @param dataIdPrefix   单个配置项的 dataId 前缀
 * @param indexDataId    索引 dataId
 * @param mapper         原始 JSON → 类型化快照的转换函数；返回 null 表示解析失败，将触发降级。
 *                       直接实例化时必须提供；子类可传 null 并 override [mapKeyToSnapshot] 替代。
 */
open class NacosKeyedConfigSubscriber<T : Any>(
    protected val configService: ConfigService,
    /** Nacos 分组名 */
    protected val group: String,
    /** 单个配置项的 dataId 前缀 */
    protected val dataIdPrefix: String,
    /** 索引 dataId */
    private val indexDataId: String,
    /** 原始 JSON → 类型化快照的转换函数（可选，子类可 override [mapKeyToSnapshot] 替代） */
    private val mapper: ((key: String, content: String) -> T?)? = null
) : AbstractKeyedConfigSubscriber<T>() {

    protected val listenerExecutor: Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("nacos-${javaClass.simpleName}-listener", 0).factory()
    )

    private val indexListenerRegistered = AtomicBoolean(false)

    // ─── 解析管线 ──────────────────────────────────────────────────

    /**
     * 将原始配置内容映射为类型化快照。
     *
     * 双路径解析：
     * - 若构造时提供了 [mapper] → 委托给 mapper
     * - 否则由子类 override 此方法提供自定义解析
     *
     * 返回 null 表示解析失败，调用方将触发降级。
     */
    override fun mapKeyToSnapshot(key: String, content: String): T? = mapper?.invoke(key, content)

    // ─── SPI 实现 ──────────────────────────────────────────────────

    override fun loadAll(): List<T> {
        val keys = loadIndex()
        val result = keys.mapNotNull { get(it) }
        log.info("Loaded ${result.size} configs from Nacos [${javaClass.simpleName}]")
        return result
    }

    override fun get(key: String): T? {
        val dataId = dataIdPrefix + key
        return try {
            val content = configService.getConfig(dataId, group, 5000)
            if (content.isNullOrBlank()) null
            else resolveAndGet(key, content)
        } catch (e: Exception) {
            log.error("Failed to get config from Nacos: dataId=$dataId", e)
            null
        }
    }

    override fun watch(listener: KeyedConfigChangeListener<T>) {
        super.watch(listener)
        if (indexListenerRegistered.compareAndSet(false, true)) {
            registerIndexListener()
        }
    }

    // ─── 可覆盖的钩子 ──────────────────────────────────────────────

    /**
     * 处理索引变更事件。
     *
     * 默认实现：解析新索引集合，对每个 key 重新拉取内容并触发 UPDATE。
     * 适用于不需要 diff 对比的简单场景（如工作流定义）。
     *
     * 子类可 override 以实现更复杂的策略（如 diff 对比 PUBLISH/REMOVE/UPDATE + 独立 dataId 监听器）。
     */
    protected open fun handleIndexChange(newIndexContent: String) {
        try {
            val newIds = SnapshotParser.parseFunctionIndex(newIndexContent, log)
            newIds.forEach { key -> get(key)?.let { notifyListeners(key, it, ChangeType.UPDATE) } }
            log.debug("Processed index change, ${newIds.size} configs refreshed")
        } catch (e: Exception) {
            log.error("Failed to handle index change from Nacos", e)
        }
    }

    // ─── 内部方法 ─────────────────────────────────────────────────

    protected fun loadIndex(): List<String> {
        return try {
            val content = configService.getConfig(indexDataId, group, 5000)
            if (content.isNullOrBlank()) emptyList()
            else SnapshotParser.parseFunctionIndex(content, log).toList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to load index from Nacos: $indexDataId", e)
        }
    }

    private fun registerIndexListener() {
        try {
            configService.addListener(indexDataId, group, object : Listener {
                override fun getExecutor(): Executor = listenerExecutor
                override fun receiveConfigInfo(configInfo: String) {
                    handleIndexChange(configInfo)
                }
            })
            log.info("Registered Nacos listener on index dataId=$indexDataId")
        } catch (e: Exception) {
            throw RuntimeException("Failed to register Nacos index listener", e)
        }
    }
}
