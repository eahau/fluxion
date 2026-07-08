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
 *    per-call.
 * 3. **Shutdown phase** – the container calls [shutdown] on JVM exit to
 *    release any transport-held resources (channels, registry subscriptions).
 *
 * The concrete HTTP/gRPC/Dubbo implementations live in the `:http`,
 * `:grpc`, `:dubbo` submodules respectively; a single
 * [ExternalFunctionTransportRegistry] coordinates discovery and lifecycle
 * across all of them.
 */
package com.fluxion.core.function.external

/**
 * Pluggable RPC boundary used by EXTERNAL nodes to leave the worker JVM.
 */
interface ExternalFunctionTransport {

    /** Lowercase protocol identifier matched against configs ("http", "grpc", "dubbo"). */
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
    fun prepare(configs: List<ExternalFunctionConfig>) {}

    /**
     * Synchronously executes a single external-function invocation.
     *
     * Implementations MUST convert any transport-layer exceptions into a
     * failed [ExternalFunctionResponse] instead of rethrowing; the engine
     * treats a thrown exception as a node failure and will apply retry /
     * compensation policy based on the error strategy.
     *
     * @param request carries routing config + serialised input payload
     * @return a response envelope describing success or structured failure
     */
    fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse

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
