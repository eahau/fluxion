package com.fluxion.config.core

import org.slf4j.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 通用配置订阅器抽象基类 — 统一管理监听器注册与生命周期
 *
 * 将三套并行的 Abstract*Subscriber（Definition/Function/Idempotency）收敛为单一泛型实现。
 * 各具体实现只需关注：
 * - 配置中心的 **namespace**（Apollo namespace / Nacos dataId）
 * - 配置 **key**（Apollo property key / Nacos 内容键）
 * - **configMapper**（原始字符串 → 类型化配置对象的映射器）
 *
 * 监听器管理与安全通知由本基类统一提供，子类通过 [notifyListeners]
 * 触发回调即可，无需重复编写 CopyOnWriteArrayList + try/catch 逻辑。
 *
 * @param L 监听器类型（如 ConfigChangeListener<T>、FunctionChangeListener 等）
 */
abstract class AbstractConfigSubscriber<L> {

    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    private val listeners = CopyOnWriteArrayList<L>()

    /** 注册监听器 */
    protected fun addListener(listener: L) {
        listeners.add(listener)
    }

    /** 获取当前所有已注册监听器的不可变快照（用于遍历通知） */
    protected fun listeners(): List<L> = listeners.toList()

    /**
     * 安全地遍历监听器并执行回调。
     *
     * 子类在收到配置中心推送事件时调用此方法，
     * 提供具体的通知逻辑 [action]；任一监听器抛出异常不影响其他监听器。
     */
    protected inline fun notifyListeners(action: (L) -> Unit) {
        for (listener in listeners()) {
            try {
                action(listener)
            } catch (e: Exception) {
                log.error("Config listener error", e)
            }
        }
    }
}
