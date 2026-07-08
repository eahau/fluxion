/**
 * Adapter that converts a low-level `FunctionConfigSubscriber` into the
 * richer `FunctionMetaConfigCenter` surface.
 *
 * The three first-party config backends (Apollo, Nacos, internal HTTP push)
 * already parse raw payloads into `FunctionConfigSnapshot` via the shared
 * `FunctionConfigSubscriber` SPI in `fluxion-adapter-spi`; duplicating
 * snapshot-to-meta conversion in each backend would be wasteful. Instead
 * each backend auto-configuration instantiates this class once with the
 * appropriate subscriber and registers it under the backend's unique
 * [name].
 *
 * Per-key unsubscription is intentionally a no-op: config-centre
 * subscriptions live for the whole application lifetime because the shared
 * subscriber dispatches every event globally rather than per watch handle.
 */
package com.fluxion.functionmeta.api

import com.fluxion.adapter.spi.config.ChangeType
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.adapter.spi.config.FunctionConfigSubscriber
import com.fluxion.core.value.FunctionMeta

/**
 * Bridges a snapshot-style subscriber to the metadata config-centre API.
 *
 * @property subscriber raw change dispatcher supplied by the backend
 * @property name backend identifier reported to the selector
 */
class SubscriberBackedFunctionMetaConfigCenter(
    private val subscriber: FunctionConfigSubscriber,
    override val name: String
) : FunctionMetaConfigCenter {

    override fun load(functionName: String): FunctionMeta? =
        subscriber.get(functionName)?.toFunctionMeta()

    override fun watch(functionName: String, listener: (FunctionMeta) -> Unit): Subscription {
        subscriber.watch { key, snapshot, changeType ->
            if (key == functionName && changeType != ChangeType.REMOVE) {
                listener(snapshot.toFunctionMeta())
            }
        }
        return object : Subscription {
            // Shared subscriber dispatches globally; per-key unsubscribe
            // would require reference counting we do not need for a
            // process-lifetime listener so we just no-op the close.
            override fun close() { /* lifecycle-scoped, no unsub needed */ }
        }
    }

    override fun functionNames(): Collection<String> =
        subscriber.loadAll().map { it.functionName }

    private fun FunctionConfigSnapshot.toFunctionMeta(): FunctionMeta =
        FunctionMeta.builder(functionName)
            .description(description)
            .paramSchema(paramSchema)
            .outputSchema(outputSchema)
            .build()
}
