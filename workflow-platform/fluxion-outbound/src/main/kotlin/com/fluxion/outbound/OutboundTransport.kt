/**
 * Cross-process RPC transport SPI used by `EXTERNAL` workflow nodes.
 *
 * Workflow definitions authored via the admin console often need to reach
 * services outside the Fluxion worker JVM (HTTP endpoints, gRPC backends,
 * internal Dubbo services). Rather than baking a single RPC stack into the
 * engine, we model the call boundary as a pluggable transport:
 *
 * 1. **Prepare phase** – the registry hands a batch of configs to each
 *    transport during startup so it can eagerly build and cache expensive
 *    resources (gRPC channels, Dubbo `ReferenceConfig`, connection pools).
 *    Failures here bubble up at boot (fail-fast) instead of on the first
 *    production call.
 * 2. **Invoke phase** – the engine forwards per-node requests. Implementors
 *    SHOULD reuse the resources created in [prepare] rather than building
 *    per-call. Two invocation paths are available:
 *    - [invoke] – blocking bridge for non-coroutine adapters
 *    - [invokeSuspend] – primary coroutine-native path for async I/O
 * 3. **Shutdown phase** – the container calls [shutdown] on JVM exit to
 *    release any transport-held resources (channels, registry subscriptions).
 *
 * The concrete HTTP/gRPC/Dubbo implementations live in the `:http`,
 * `:grpc`, `:dubbo` submodules respectively; a single
 * [OutboundTransportRegistry] coordinates discovery and lifecycle
 * across all of them.
 */
package com.fluxion.outbound

import kotlinx.coroutines.runBlocking

/**
 * Pluggable RPC boundary used by EXTERNAL nodes to leave the worker JVM.
 *
 * Mirroring the [com.fluxion.inbound.spi.InboundRouter] pattern, this interface
 * provides both blocking [invoke] and coroutine-native [invokeSuspend] methods,
 * with default implementations that bridge between them via [runBlocking].
 * Implementations should override [invokeSuspend] for true async I/O (e.g.,
 * non-blocking HTTP clients, gRPC future calls, CompletableFuture-based Dubbo).
 */
interface OutboundTransport {

    /** Lowercase protocol identifier matched against configs ("http", "grpc", "dubbo", "mq"). */
    fun protocol(): String

    /**
     * Eagerly initialises expensive resources for the supplied configs.
     *
     * Invoked once during startup by the outer registry. Default no-op so
     * lightweight transports (e.g. process-local bridges) can skip it.
     *
     * @param configs every external-function config the operator declared,
     *   already bucketed by this transport's protocol
     */
    fun prepare(configs: List<OutboundConfig>) {}

    /**
     * Blocking bridge for non-coroutine adapters (Dubbo/gRPC/Kafka consumer).
     * Bridges the call onto the coroutine dispatcher via [runBlocking].
     *
     * Implementations MUST convert any transport-layer exceptions into a
     * failed [OutboundResponse] instead of rethrowing; the engine
     * treats a thrown exception as a node failure and will apply retry /
     * compensation policy based on the error strategy.
     *
     * @param request carries routing config + serialised input payload
     * @return a response envelope describing success or structured failure
     */
    fun invoke(request: OutboundRequest): OutboundResponse = runBlocking { invokeSuspend(request) }

    /**
     * Primary coroutine-native invocation path. Suspends instead of blocking
     * the calling thread, which is essential for WebFlux / virtual-thread pools.
     *
     * Implementations MUST convert any transport-layer exceptions into a
     * failed [OutboundResponse] instead of rethrowing; the engine
     * treats a thrown exception as a node failure and will apply retry /
     * compensation policy based on the error strategy.
     *
     * @param request carries routing config + serialised input payload
     * @return a response envelope describing success or structured failure
     */
    suspend fun invokeSuspend(request: OutboundRequest): OutboundResponse = invoke(request)

    /**
     * Returns true if this transport is one-way (fire-and-forget), meaning
     * the caller should not wait for a response. MQ transports typically
     * return true here, while HTTP/gRPC/Dubbo return false.
     *
     * The engine uses this flag to optimize execution: for one-way transports,
     * it skips response-waiting logic and treats the call as immediately
     * successful after dispatch.
     *
     * @return true for fire-and-forget protocols (MQ), false for request-response
     */
    fun isOneWay(): Boolean = false

    /**
     * Releases resources held by this transport.
     *
     * Idempotent: callers may invoke multiple times; implementations should
     * guard with an `AtomicBoolean` CAS or similar so repeated calls are
     * safe. Invoked by the outer registry during JVM shutdown hooks and
     * admin-triggered rolling restarts.
     */
    fun shutdown() {}
}