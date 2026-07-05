package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.config.core.SnapshotParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Nacos 实现 — 函数配置订阅器（业务实例侧）
 *
 * 继承 [NacosKeyedConfigSubscriber] 的索引获取、监听器注册和 dataId 读取能力，
 * 通过 override [handleIndexChange] 实现 diff 对比策略：
 * 对比旧/新函数集合，分别触发 PUBLISH / REMOVE / UPDATE，
 * 同时为每个函数注册独立的 dataId 监听器，确保函数内容变更能被感知。
 */
class NacosFunctionConfigSubscriber(
    configService: ConfigService
) : NacosKeyedConfigSubscriber<FunctionConfigSnapshot>(
    configService = configService,
    group         = "WORKFLOW",
    dataIdPrefix  = "workflow.function.",
    indexDataId   = "workflow.function.__index__"
) {

    /** 当前已监听的函数集合，用于在索引变化时判断新增/删除 */
    private val watchedFunctions = ConcurrentHashMap.newKeySet<String>()

    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot? =
        SnapshotParser.parseFunctionSnapshot(content, key, log)

    override fun loadAll(): List<FunctionConfigSnapshot> {
        val result = super.loadAll()
        // 启动时即为每个函数注册内容监听器，确保后续函数内容变更能被感知
        val keys = loadIndex()
        keys.forEach { registerFunctionListener(it) }
        return result
    }

    /**
     * diff 对比策略 — 对比旧/新集合，分别触发 PUBLISH / REMOVE / UPDATE。
     *
     * 覆盖基类的"全量刷新"默认实现，以实现精确的增量变更通知。
     */
    override fun handleIndexChange(newIndexContent: String) {
        try {
            val newIdSet = SnapshotParser.parseFunctionIndex(newIndexContent, log)
            val oldIdSet = watchedFunctions.toSet()

            val added   = newIdSet - oldIdSet
            val removed = oldIdSet - newIdSet
            val updated = newIdSet intersect oldIdSet

            // 新增：注册单个函数监听器并触发 PUBLISH
            for (functionName in added) {
                registerFunctionListener(functionName)
                get(functionName)?.let { notifyListeners(functionName, it, ChangeType.PUBLISH) }
            }

            // 删除：注销监听器、清理降级缓存并触发 REMOVE
            for (functionName in removed) {
                watchedFunctions.remove(functionName)
                evictSnapshot(functionName)
                notifyListeners(functionName, FunctionConfigSnapshot.removed(functionName), ChangeType.REMOVE)
            }

            // 保留集合中可能内容已变化，统一触发 UPDATE（幂等注册监听器）
            for (functionName in updated) {
                get(functionName)?.let { notifyListeners(functionName, it, ChangeType.UPDATE) }
            }

            log.debug("Processed function index change, added=$added, removed=$removed, updated=$updated")
        } catch (e: Exception) {
            log.error("Failed to handle function index change from Nacos", e)
        }
    }

    // ─── 函数级别 dataId 监听 ─────────────────────────────────────

    private fun registerFunctionListener(functionName: String) {
        if (!watchedFunctions.add(functionName)) return
        val dataId = dataIdPrefix + functionName
        try {
            configService.addListener(dataId, group, object : Listener {
                override fun getExecutor() = listenerExecutor
                override fun receiveConfigInfo(configInfo: String) {
                    handleFunctionChange(functionName, configInfo)
                }
            })
        } catch (e: Exception) {
            watchedFunctions.remove(functionName)
            log.error("Failed to register Nacos function listener: dataId=$dataId", e)
        }
    }

    private fun handleFunctionChange(functionName: String, configInfo: String) {
        if (configInfo.isBlank()) {
            evictSnapshot(functionName)
            notifyListeners(functionName, FunctionConfigSnapshot.removed(functionName), ChangeType.REMOVE)
            return
        }
        val snap = resolveAndGet(functionName, configInfo) ?: return
        notifyListeners(functionName, snap, ChangeType.UPDATE)
    }
}
