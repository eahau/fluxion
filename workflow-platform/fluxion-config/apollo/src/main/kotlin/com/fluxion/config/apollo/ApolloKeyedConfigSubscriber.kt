/**
 * Apollo-backed multi-key configuration subscriber with dual-path resolution.
 *
 * Provides a concrete, directly-instantiable subscriber implementation for
 * Ctrip Apollo namespace watch/load patterns. Encapsulates namespace lookup,
 * listener registration (guarded by an [AtomicBoolean] to prevent double
 * wiring), property-name iteration semantics for `loadAll()`/`get(key)`, and
 * event dispatch from [ConfigChangeEvent] onto the [KeyedConfigChangeListener]
 * SPI.
 *
 * Typed snapshot resolution and validation are delegated to the base class
 * [AbstractKeyedConfigSubscriber.resolveAndGet] uniformly.
 */
package com.fluxion.config.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigService
import com.ctrip.framework.apollo.enums.PropertyChangeType
import com.ctrip.framework.apollo.model.ConfigChangeEvent
import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.KeyedConfigChangeListener
import com.fluxion.config.core.AbstractKeyedConfigSubscriber
import org.slf4j.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Concrete keyed-config subscriber that reads entries from an Apollo namespace.
 *
 * Two usage styles are supported:
 * 1. Direct instantiation — pass constructor lambdas [mapper] / [removedMapper]
 *    for simple snapshot types.
 * 2. Subclassing — override [mapKeyToSnapshot] / [buildRemovedSnapshot] when
 *    the mapping requires instance state such as a child-class logger.
 *
 * ### Usage: direct instantiation
 * ```kotlin
 * ApolloKeyedConfigSubscriber(
 *     namespace     = "workflow-definitions",
 *     mapper        = { key, content -> WorkflowDefinitionSnapshot.of(key, content, 0, true) },
 *     removedMapper = { key -> WorkflowDefinitionSnapshot.removed(key) })
 * ```
 *
 * ### Usage: subclassing
 * ```kotlin
 * class ApolloFunctionConfigSubscriber :
 *     ApolloKeyedConfigSubscriber<FunctionConfigSnapshot>(namespace = "workflow-functions") {
 *     override fun mapKeyToSnapshot(key: String, content: String) =
 *         SnapshotParser.parseFunctionSnapshot(content, key, log)
 *     override fun buildRemovedSnapshot(key: String) = FunctionConfigSnapshot.removed(key)
 * }
 * ```
 *
 * @param T            snapshot value type produced by this subscriber.
 * @param namespace    Apollo namespace identifier passed to [ConfigService.getConfig].
 * @param mapper       optional `(key, rawContent) -> T?` lambda used as the
 *                     default implementation of [mapKeyToSnapshot].
 * @param removedMapper optional `(key) -> T` lambda used as the default
 *                      implementation of [buildRemovedSnapshot].
 */
open class ApolloKeyedConfigSubscriber<T : Any>(
    private val namespace: String,
    private val mapper: ((key: String, content: String) -> T)? = null,
    private val removedMapper: ((key: String) -> T)? = null
) : AbstractKeyedConfigSubscriber<T>() {

    private val config: Config by lazy { ConfigService.getConfig(namespace) }
    private val listenerAdded = AtomicBoolean(false)

    // ── Dual-path resolution ──────────────────────────────────────────────

    /**
     * Map raw string content for a key into a typed snapshot.
     *
     * Dual-path: delegates to the constructor [mapper] when present, otherwise
     * expects subclasses to override this method.
     *
     * @param key     configuration key as it appears in the Apollo namespace.
     * @param content raw textual content stored under the key.
     * @return parsed snapshot or `null` if the content should be skipped.
     */
    override fun mapKeyToSnapshot(key: String, content: String): T? = mapper?.invoke(key, content)

    /**
     * Build a tombstone snapshot that represents removal of [key].
     *
     * Dual-path: delegates to [removedMapper] when present, otherwise expects
     * subclasses to override this method.
     *
     * @throws UnsupportedOperationException if no mapper is provided and the
     *                                       method is not overridden.
     */
    protected open fun buildRemovedSnapshot(key: String): T =
        removedMapper?.invoke(key)
            ?: throw UnsupportedOperationException(
                "buildRemovedSnapshot not implemented: provide removedMapper or override this method")

    // ── SPI implementations (standard pattern) ────────────────────────────

    override fun loadAll(): List<T> {
        val keys = config.propertyNames
        val result = keys.mapNotNull { key ->
            val json = config.getProperty(key, null)
            if (!json.isNullOrBlank()) resolveAndGet(key, json) else null
        }
        log.info { "Loaded ${result.size} configs from Apollo namespace [$namespace]" }
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
            log.info { "Registered Apollo change listener on namespace [$namespace]" }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

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
