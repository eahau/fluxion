/**
 * Pluggable source-of-truth for operator-authored function metadata.
 *
 * Runtime workers never speak directly to a database; instead they stream
 * metadata changes from a config distribution layer. This interface models
 * that distribution layer with a minimal load/watch/list contract. Three
 * first-class backends ship with Fluxion:
 * * Apollo – `fluxion-config:apollo`
 * * Nacos  – `fluxion-config:nacos`
 * * Internal HTTP+DB push – `fluxion-config:http` (admin publishes, worker
 *   subscribes via `SchemaPushController`/`FunctionPushController`)
 *
 * Each backend auto-configuration exposes a bean of this type with a unique
 * [name] tag; [FunctionMetaConfigCenterSelector] then picks the active one
 * based on the `fluxion.meta.config-center` property.
 *
 * The adapter layer already provides a `FunctionConfigSubscriber` that
 * parses raw payloads into `FunctionConfigSnapshot`; most backend
 * implementations reuse that parsing and simply wrap the subscriber with
 * [SubscriberBackedFunctionMetaConfigCenter] rather than reimplementing the
 * full contract from scratch.
 */
package com.fluxion.functionmeta.api

import com.fluxion.core.value.FunctionMeta

/**
 * Metadata distribution source used by `FunctionMetaConfigApplier` at
 * bootstrap and on every push event thereafter.
 */
interface FunctionMetaConfigCenter {

    /** Stable identifier used for operator selection via properties. */
    val name: String

    /**
     * Synchronously fetches the currently published metadata for a single
     * function.
     *
     * Invoked during worker startup so the registry can be seeded before the
     * engine accepts traffic.
     *
     * @param functionName canonical reference to look up
     * @return the latest published metadata or `null` if the key is unknown
     */
    fun load(functionName: String): FunctionMeta?

    /**
     * Subscribes to change events for a single function.
     *
     * Implementations SHOULD invoke `listener` at least once with the
     * current value on subscription so late subscribers converge. The
     * returned [Subscription] is a no-op closeable for most push-style
     * backends because subscriptions live for the process lifetime.
     *
     * @param functionName canonical reference to watch
     * @param listener callback invoked on every non-REMOVE change
     * @return handle that can be closed to unsubscribe
     */
    fun watch(functionName: String, listener: (FunctionMeta) -> Unit): Subscription

    /**
     * Enumerates every function key currently published by this centre.
     *
     * Used by the bootstrap loader to backfill the in-memory registry.
     */
    fun functionNames(): Collection<String>
}

/**
 * Handle returned by [FunctionMetaConfigCenter.watch]; closing it releases
 * any backend-held subscription resources.
 */
interface Subscription : AutoCloseable {
    override fun close()
}

/** Lambda alias matching the watch callback signature. */
typealias MetaUpdateListener = (FunctionMeta) -> Unit
