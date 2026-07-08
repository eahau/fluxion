package com.fluxion.functionmeta.spring.boot

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionChangeListener
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.functionmeta.registry.InMemoryFunctionMetaRegistry
import org.slf4j.*
/**
 * Worker-side adapter that translates config-center events into
 * [InMemoryFunctionMetaRegistry] mutations.
 *
 * The data flow on a Worker node is:
 * ```
 * Admin WfFunctionService → FunctionConfigPublisher (Nacos/Apollo/HTTP)
 *         ↓ (config push)
 * FunctionConfigSubscriber → FunctionMetaConfigApplier → InMemoryFunctionMetaRegistry
 * ```
 *
 * On [init] this performs a bulk load of every existing
 * [FunctionConfigSnapshot] via [FunctionConfigSubscriber.loadAll], then
 * subscribes to incremental [FunctionChangeListener.onChange] events.
 * Snapshot → [com.fluxion.core.value.FunctionMeta] conversion is handled by
 * `InMemoryFunctionMetaRegistry.register(snapshot)` so this class stays
 * transport-agnostic.
 *
 * Error-handling strategy: per-change exceptions are caught and logged, but
 * never propagated to the subscriber. This prevents a single bad payload from
 * tearing down the whole push channel; subsequent (healthy) events will still
 * arrive and eventually converge state.
 *
 * BUILTIN functions skip this path entirely because their metadata is shipped
 * with the code and registered eagerly by
 * `com.fluxion.script.config.FunctionConfigApplier` /
 * `com.fluxion.builtin.meta.*` during startup.
 */
class FunctionMetaConfigApplier(
    private val subscriber: FunctionConfigSubscriber,
    private val registry: InMemoryFunctionMetaRegistry
) : FunctionChangeListener {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Performs the initial bulk-load of every published FunctionMeta and then
     * subscribes for future push events.
     *
     * Called from the auto-configuration bean factory method BEFORE the bean is
     * returned—this guarantees the registry is populated before any downstream
     * component (e.g. workflow engine) sees it.
     */
    fun init() {
        val snapshots = subscriber.loadAll()
        for (snap in snapshots) {
            applySnapshot(snap, ChangeType.PUBLISH)
        }
        log.info { "FunctionMetaConfigApplier initialized with ${snapshots.size} function metas" }
        subscriber.watch(this)
    }

    /**
     * Handles incremental push events dispatched by the subscriber.
     *
     * Failures are swallowed locally so one bad update cannot break the whole
     * streaming channel; the next healthy event will converge state.
     */
    override fun onChange(key: String, snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        try {
            applySnapshot(snapshot, changeType)
        } catch (e: Exception) {
            log.error(e) { "Failed to apply function meta config change: name=${snapshot.functionName}, type=$changeType" }
        }
    }

    private fun applySnapshot(snapshot: FunctionConfigSnapshot, changeType: ChangeType) {
        when (changeType) {
            ChangeType.REMOVE -> {
                registry.unregister(snapshot.functionName)
                log.info { "Removed function meta [${snapshot.functionName}] from registry" }
            }
            else -> {
                // Treat PUBLISH / UPDATE uniformly: disabled snapshots are
                // removed from the active registry so engine lookups fail fast
                // instead of returning stale-but-marked-disabled data.
                if (!snapshot.enabled) {
                    registry.unregister(snapshot.functionName)
                    return
                }
                registry.register(snapshot)
                log.info {
                    "Applied function meta [${snapshot.functionName}] type=${snapshot.functionType} changeType=$changeType"
                }
            }
        }
    }
}
