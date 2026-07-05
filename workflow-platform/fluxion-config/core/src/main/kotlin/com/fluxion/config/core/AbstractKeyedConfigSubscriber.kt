package com.fluxion.config.core

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.KeyedConfigChangeListener
import com.fluxion.adapter.spi.config.KeyedConfigSubscriber
import java.util.concurrent.ConcurrentHashMap

/**
 * 多键配置订阅器抽象基类 — 统一管理监听器注册、带键通知、配置校验与降级
 *
 * 将 Definition/Function 两套并行的 Abstract*Subscriber 收敛为单一泛型实现。
 * 各具体实现（Apollo/Nacos/HTTP）只需关注：
 * - **namespace**：配置中心的命名空间
 * - **key 模式**：索引键或前缀规则
 * - **configMapper**：原始字符串 → 类型化快照的映射器
 *
 * ## 校验与降级管线
 *
 * 配置变更时通过 [resolveAndGet] 统一执行以下管线：
 *
 * ```
 * rawContent → mapKeyToSnapshot → validate → Success: 更新 lastValid + 通知监听器
 *                                         → ParseFailed / ValidationFailed: 回退到 lastValid[key]
 * ```
 *
 * - **格式校验**：`mapKeyToSnapshot` 内部 JSON 解析失败 → [ConfigResolveResult.ParseFailed]
 * - **业务校验**：子类覆盖 [validate] 检查必填字段等 → [ConfigResolveResult.ValidationFailed]
 * - **降级**：解析或校验失败时自动回退到 [lastValid] 中缓存的上一个好值
 *
 * @param T 配置快照类型（如 WorkflowDefinitionSnapshot、FunctionConfigSnapshot）
 */
abstract class AbstractKeyedConfigSubscriber<T : Any> :
    AbstractConfigSubscriber<KeyedConfigChangeListener<T>>(),
    KeyedConfigSubscriber<T> {

    /** 每个 key 最近一次通过解析 + 校验的快照，用于变更失败时的降级回退 */
    private val lastValid = ConcurrentHashMap<String, T>()

    override fun watch(listener: KeyedConfigChangeListener<T>) {
        addListener(listener)
    }

    // ─── 校验钩子 ────────────────────────────────────────────────────

    /**
     * 业务逻辑校验钩子 — 子类可覆盖以检查必填字段、值范围等约束。
     *
     * 在 `mapKeyToSnapshot` 解析成功后调用。
     * 返回 `true` 表示通过；返回 `false` 则该快照被拒绝，
     * 管线将回退到 [lastValid] 中的上一个好值。
     *
     * 默认实现始终返回 `true`（不施加额外约束）。
     *
     * @param key      配置键
     * @param snapshot 解析后的快照对象
     * @return true = 通过；false = 拒绝
     */
    protected open fun validate(key: String, snapshot: T): Boolean = true

    // ─── Resolve 管线 ────────────────────────────────────────────────

    /**
     * 解析 + 校验 + 降级的统一管线。
     *
     * 子类在 `handleConfigChange` / `get` 中调用此方法替代直接调用 `mapKeyToSnapshot`，
     * 以获得一致的校验和降级行为：
     *
     * 1. 调用 [mapKeyToSnapshot] 解析原始内容为快照
     * 2. 调用 [validate] 执行业务校验
     * 3. 任一环节失败 → 回退到 [lastValid] 中缓存的上一个好值
     * 4. 成功 → 更新 [lastValid] 并返回快照
     *
     * @param key         配置键
     * @param rawContent  原始配置内容（JSON 字符串）
     * @return 可用的快照（可能是新解析的或降级回退的）；若首次且失败则返回 null
     */
    protected fun resolveAndGet(key: String, rawContent: String): T? {
        val snapshot = try {
            mapKeyToSnapshot(key, rawContent)
        } catch (e: Exception) {
            log.warn("Config parse failed for key=[$key]: ${e.message}")
            return fallback(key, ConfigResolveResult.ParseFailed(e))
        }

        if (snapshot == null) {
            log.warn("Config parse returned null for key=[$key]")
            return fallback(key, ConfigResolveResult.ParseFailed(
                IllegalStateException("mapKeyToSnapshot returned null")))
        }

        if (!validate(key, snapshot)) {
            log.warn("Config validation failed for key=[$key]")
            return fallback(key, ConfigResolveResult.ValidationFailed("validate() returned false"))
        }

        trackSnapshot(key, snapshot)
        return snapshot
    }

    // ─── 快照生命周期管理 ────────────────────────────────────────────

    /**
     * 记录一个已通过解析 + 校验的快照。
     *
     * 由 `loadAll()` 在初始加载时调用，确保降级缓存有初始值。
     * `resolveAndGet` 内部会自动调用，无需在变更处理中重复调用。
     */
    protected fun trackSnapshot(key: String, snapshot: T) {
        lastValid[key] = snapshot
    }

    /**
     * 移除某个 key 的降级缓存。
     *
     * 子类在处理 REMOVE 事件时调用，避免内存泄漏。
     */
    protected fun evictSnapshot(key: String) {
        lastValid.remove(key)
    }

    // ─── 内部方法 ────────────────────────────────────────────────────

    /**
     * 通知所有监听器某个键的配置发生变更。
     * 子类在收到后端推送/拉取事件时调用。
     */
    protected fun notifyListeners(key: String, snapshot: T, type: ChangeType) {
        notifyListeners { it.onChange(key, snapshot, type) }
    }

    /**
     * 将原始配置内容映射为类型化快照。
     *
     * 由各后端实现（Apollo/Nacos/HTTP）提供具体解析逻辑。
     * 返回 null 表示解析失败，调用方将触发降级。
     */
    protected abstract fun mapKeyToSnapshot(key: String, content: String): T?

    /**
     * 降级回退 — 返回上一个好值或 null。
     */
    private fun fallback(key: String, result: ConfigResolveResult<*>): T? {
        val last = lastValid[key]
        when (result) {
            is ConfigResolveResult.ParseFailed ->
                log.warn("Degradation: key=[$key] parse failed, " +
                    "fallback=${if (last != null) "lastValid" else "null(skip)"}",
                    result.error)
            is ConfigResolveResult.ValidationFailed ->
                log.warn("Degradation: key=[$key] validation failed (${result.reason}), " +
                    "fallback=${if (last != null) "lastValid" else "null(skip)"}")
            is ConfigResolveResult.Success -> {} // unreachable
        }
        return last
    }
}
