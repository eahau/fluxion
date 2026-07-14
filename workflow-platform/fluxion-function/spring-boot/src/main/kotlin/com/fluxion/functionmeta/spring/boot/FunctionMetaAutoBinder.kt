package com.fluxion.functionmeta.spring.boot

import com.fluxion.core.function.FunctionRegistry
import com.fluxion.functionmeta.api.FunctionMetaConfigCenter
import com.fluxion.functionmeta.api.Subscription
import org.slf4j.*
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds metadata loaded from a [FunctionMetaConfigCenter] into the live
 * [FunctionRegistry] after Spring context startup.
 *
 * Listens for [ContextRefreshedEvent] so it runs after all singleton beans
 * (including the registry and every registered WorkflowFunction) are fully
 * available. On first refresh it:
 * 1. Iterates every function already in the registry.
 * 2. Calls [FunctionMetaConfigCenter.load] for an immediate fetch of its
 *    metadata, pushing the result into [FunctionRegistry.updateMeta].
 * 3. Installs a push watcher ([FunctionMetaConfigCenter.watch]) so that
 *    subsequent metadata edits in the Admin are reflected on this node
 *    without a restart.
 *
 * This replaces the older `FunctionMetaBinder` SPI that required functions
 * to re-register themselves on change. Here the registry is keyed by
 * [com.fluxion.core.function.WorkflowFunction.functionName], and metadata is
 * a *sidecar* attached via `updateMeta`—the function instance itself is
 * never replaced, so in-flight executions stay safe.
 *
 * Subscriptions are tracked by function name and released on [close].
 */
class FunctionMetaAutoBinder(
    private val functionRegistry: FunctionRegistry,
    private val configCenter: FunctionMetaConfigCenter
) : ApplicationListener<ContextRefreshedEvent> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val subscriptions = ConcurrentHashMap<String, Subscription>()

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        try {
            bindAll()
        } catch (e: Exception) {
            log.error(e) { "FunctionMeta auto-binding failed" }
        }
    }

    /**
     * Walks every currently-registered function and binds metadata for each.
     *
     * Called exactly once on context refresh; individual functions can be
     * re-bound later via [bind] if they are registered after startup.
     */
    private fun bindAll() {
        val names = functionRegistry.names().toList()
        var bound = 0
        for (name in names) {
            if (bind(name)) bound++
        }
        log.info { "FunctionMetaAutoBinder: bound $bound/${names.size} functions via config-center [${configCenter.name}]" }
    }

    /**
     * Loads metadata for a single function and installs a push watcher.
     *
     * Idempotent: if the name is already subscribed, this is a no-op returning
     * `true` so callers can count successes uniformly.
     *
     * @param name function identifier, matching
     *   [com.fluxion.core.function.WorkflowFunction.functionName]
     * @return `true` on success (or already bound); `false` only when the
     *   config center rejects the name entirely.
     */
    fun bind(name: String): Boolean {
        if (subscriptions.containsKey(name)) return true
        configCenter.load(name)?.let { functionRegistry.updateMeta(name, it) }
        val sub = configCenter.watch(name) { newMeta ->
            functionRegistry.updateMeta(name, newMeta)
            log.debug { "Function meta updated from config-center: `$name" }
        }
        subscriptions[name] = sub
        return true
    }

    /** Releases all watch subscriptions and clears tracking state. */
    fun close() {
        subscriptions.values.forEach { runCatching { it.close() } }
        subscriptions.clear()
    }
}
