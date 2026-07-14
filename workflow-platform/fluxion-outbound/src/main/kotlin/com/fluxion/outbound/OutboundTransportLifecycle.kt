/**
 * Tiny lifecycle helper used by concrete [OutboundTransport]
 * implementations to manage two common, often-mishandled concerns:
 *
 * 1. **Resource shutdown hooks** – registered with [onShutdown], executed
 *    in reverse order during [shutdown] so channels, reference configs and
 *    other closeable resources are deterministically released even when
 *    individual hook lambdas throw.
 * 2. **Cache-clearing hooks** – registered with [onClearCache] and invoked
 *    after shutdown hooks complete; this mirrors the `clear()` step most
 *    pool implementations need to actually release heap after shutting
 *    down the underlying connection manager.
 *
 * Typical usage in a transport:
 * ```kotlin
 * class MyTransport : OutboundTransport {
 *     private val lifecycle = OutboundTransportLifecycle()
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
 * Hook ordering is FIRST-registration FIRST-execution for shutdown
 * (then reversed so last-built is closed first – stack order) and
 * straightforward iteration order for cache clears.
 */
package com.fluxion.outbound

import org.slf4j.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registry and runner for per-transport shutdown and cache-clear callbacks.
 */
class OutboundTransportLifecycle {

    private val log = LoggerFactory.getLogger(OutboundTransportLifecycle::class.java)
    private val shutdown = AtomicBoolean(false)
    private val shutdownHooks = mutableListOf<NamedHook>()
    private val cacheClearHooks = mutableListOf<NamedHook>()

    /** True once shutdown CAS has succeeded and no more hooks may be added. */
    fun isShutdown(): Boolean = shutdown.get()

    /**
     * Registers a resource-cleanup hook executed during [shutdown].
     *
     * Hooks are run in reverse registration order so a resource that
     * depends on another is closed before its parent. All hook exceptions
     * are caught, logged, and never suppress sibling hooks.
     *
     * @param name human-readable identifier surfaced in error logs
     * @param hook lambda that releases the resource
     * @throws IllegalStateException if registration happens after shutdown
     */
    fun onShutdown(name: String, hook: () -> Unit) {
        check(!shutdown.get()) { "Cannot register shutdown hook '$name' after lifecycle is shutdown" }
        shutdownHooks.add(NamedHook(name, hook))
    }

    /**
     * Registers a cache-clear hook run after shutdown hooks complete.
     *
     * Use this for caches that hold **references** to already-closed
     * resources (channel pools, descriptor maps) to ensure the heap
     * actually releases them. Runs in registration order; exceptions are
     * isolated per hook.
     *
     * @param name human-readable identifier surfaced in error logs
     * @param hook lambda that clears the cache
     * @throws IllegalStateException if registration happens after shutdown
     */
    fun onClearCache(name: String, hook: () -> Unit) {
        check(!shutdown.get()) { "Cannot register cache clear hook '$name' after lifecycle is shutdown" }
        cacheClearHooks.add(NamedHook(name, hook))
    }

    /**
     * Transitions to the terminal state and runs all registered hooks in
     * the documented order; idempotent on repeat calls.
     */
    fun shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            log.debug { "OutboundTransportLifecycle already shutdown, skipping" }
            return
        }
        log.info {
            "Shutting down OutboundTransportLifecycle: " +
                "${shutdownHooks.size} resource hook(s), ${cacheClearHooks.size} cache clear hook(s)"
        }
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
                LoggerFactory.getLogger(OutboundTransportLifecycle::class.java)
                    .warn(ex) { "Lifecycle hook '$name' failed" }
            }
        }
    }
}
