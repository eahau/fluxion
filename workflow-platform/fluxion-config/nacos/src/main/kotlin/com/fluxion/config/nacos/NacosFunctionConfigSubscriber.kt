/**
 * Nacos-backed subscriber for function configuration snapshots with
 * per-function granular listeners.
 *
 * Extends [NacosKeyedConfigSubscriber] and customises the index-change handler
 * so that it performs a set-diff against the previously watched function set,
 * emitting precise PUBLISH / REMOVE / UPDATE events instead of a blanket
 * full-drop-and-reload. In addition, for each individual function a dedicated
 * Nacos dataId listener is registered so content-level edits propagate
 * immediately without waiting for the `__index__` key to refresh.
 */
package com.fluxion.config.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.config.core.SnapshotParser
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Business-level subscriber that watches `WORKFLOW` group entries prefixed with
 * `workflow.function.` plus the special `workflow.function.__index__` index key.
 *
 * Diff strategy on index change:
 * ```
 * added   = newIds ∖ oldIds   → register listener + PUBLISH
 * removed = oldIds ∖ newIds   → unregister listener + evict + REMOVE
 * updated = newIds ∩ oldIds   → reload + UPDATE (listener already in place)
 * ```
 */
class NacosFunctionConfigSubscriber(
    configService: ConfigService
) : NacosKeyedConfigSubscriber<FunctionConfigSnapshot>(
    configService = configService,
    group         = "WORKFLOW",
    dataIdPrefix  = "workflow.function.",
    indexDataId   = "workflow.function.__index__"
) {

    /**
     * Names of functions whose dedicated dataId listener has been attached.
     *
     * Used by [handleIndexChange] to compute the set-diff; concurrent add/remove
     * is safe because we use a CHM-backed key set.
     */
    private val watchedFunctions = ConcurrentHashMap.newKeySet<String>()

    override fun mapKeyToSnapshot(key: String, content: String): FunctionConfigSnapshot? =
        SnapshotParser.parseFunctionSnapshot(content, key, log)

    override fun loadAll(): List<FunctionConfigSnapshot> {
        val result = super.loadAll()
        // Eagerly attach per-function listeners during bootstrap so subsequent
        // content edits propagate without waiting for an index refresh cycle.
        val keys = loadIndex()
        keys.forEach { registerFunctionListener(it) }
        return result
    }

    /**
     * Custom index-change handler that computes a precise delta against the
     * previously watched function set instead of the base-class full-drop
     * behaviour.
     *
     * This avoids spurious UPDATE notifications for functions that have not
     * actually changed between index refreshes.
     */
    override fun handleIndexChange(newIndexContent: String) {
        try {
            val newIdSet = SnapshotParser.parseFunctionIndex(newIndexContent, log)
            val oldIdSet = watchedFunctions.toSet()

            val added   = newIdSet - oldIdSet
            val removed = oldIdSet - newIdSet
            val updated = newIdSet intersect oldIdSet

            for (functionName in added) {
                registerFunctionListener(functionName)
                get(functionName)?.let { notifyListeners(functionName, it, ChangeType.PUBLISH) }
            }

            for (functionName in removed) {
                watchedFunctions.remove(functionName)
                evictSnapshot(functionName)
                notifyListeners(functionName, FunctionConfigSnapshot.removed(functionName), ChangeType.REMOVE)
            }

            for (functionName in updated) {
                get(functionName)?.let { notifyListeners(functionName, it, ChangeType.UPDATE) }
            }

            log.debug { "Processed function index change, added=$added, removed=$removed, updated=$updated" }
        } catch (e: Exception) {
            log.error(e) { "Failed to handle function index change from Nacos" }
        }
    }

    // ── Per-function dataId listener registration ─────────────────────────

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
            log.error(e) { "Failed to register Nacos function listener: dataId=$dataId" }
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
