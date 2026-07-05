package com.fluxion.core.function.external

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ExternalFunctionTransport 生命周期管理器。
 *
 * 统一维护 transport 的停机状态、资源释放钩子与缓存清空钩子，避免各协议实现
 * 重复编写幂等 shutdown、异常兜底、缓存清理等模板代码。
 *
 * 使用方式：
 * ```kotlin
 * class MyTransport : ExternalFunctionTransport {
 *     private val lifecycle = ExternalFunctionTransportLifecycle()
 *     private val cache = ConcurrentHashMap<String, MyResource>()
 *
 *     init {
 *         lifecycle.onClearCache("my-cache") { cache.clear() }
 *     }
 *
 *     private fun buildResource(config: MyConfig): MyResource {
 *         val resource = createResource(config)
 *         lifecycle.onShutdown("my-resource") { resource.close() }
 *         return resource
 *     }
 *
 *     override fun shutdown() = lifecycle.shutdown()
 * }
 * ```
 *
 * 停机执行顺序：
 * 1. 标记已停机（幂等）
 * 2. **逆序**执行所有 [onShutdown] 注册的释放钩子（后创建的先释放）
 * 3. **顺序**执行所有 [onClearCache] 注册的缓存清空钩子
 */
class ExternalFunctionTransportLifecycle {

    private val log = LoggerFactory.getLogger(ExternalFunctionTransportLifecycle::class.java)
    private val shutdown = AtomicBoolean(false)
    private val shutdownHooks = mutableListOf<NamedHook>()
    private val cacheClearHooks = mutableListOf<NamedHook>()

    /** 当前 lifecycle 是否已停机。 */
    fun isShutdown(): Boolean = shutdown.get()

    /**
     * 注册资源释放钩子。transport 在创建需要释放的远程端点后应立即注册对应的释放逻辑。
     *
     * 钩子按注册顺序的逆序执行，确保资源依赖关系正确（例如先销毁 consumer，再销毁 application model）。
     *
     * @param name 钩子名称，仅用于日志标识
     * @param hook 释放逻辑
     * @throws IllegalStateException lifecycle 已停机后不允许再注册
     */
    fun onShutdown(name: String, hook: () -> Unit) {
        check(!shutdown.get()) { "Cannot register shutdown hook '$name' after lifecycle is shutdown" }
        shutdownHooks.add(NamedHook(name, hook))
    }

    /**
     * 注册缓存清空钩子。用于在资源释放后清空 transport 内部的各类缓存（如 channelCache、descriptorCache）。
     *
     * 钩子在所有 [onShutdown] 钩子执行完毕后**顺序**执行。
     *
     * @param name 钩子名称，仅用于日志标识
     * @param hook 清空逻辑
     * @throws IllegalStateException lifecycle 已停机后不允许再注册
     */
    fun onClearCache(name: String, hook: () -> Unit) {
        check(!shutdown.get()) { "Cannot register cache clear hook '$name' after lifecycle is shutdown" }
        cacheClearHooks.add(NamedHook(name, hook))
    }

    /**
     * 执行停机。幂等：重复调用不会触发二次释放。
     */
    fun shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            log.debug("ExternalFunctionTransportLifecycle already shutdown, skipping")
            return
        }
        log.info(
            "Shutting down ExternalFunctionTransportLifecycle: " +
                "${shutdownHooks.size} resource hook(s), ${cacheClearHooks.size} cache clear hook(s)"
        )
        shutdownHooks.asReversed().forEach { it.invokeSafely() }
        shutdownHooks.clear()
        cacheClearHooks.forEach { it.invokeSafely() }
        cacheClearHooks.clear()
    }

    private data class NamedHook(val name: String, val hook: () -> Unit) {
        fun invokeSafely() {
            try {
                hook()
            } catch (ex: Exception) {
                LoggerFactory.getLogger(ExternalFunctionTransportLifecycle::class.java)
                    .warn("Lifecycle hook '$name' failed", ex)
            }
        }
    }
}
