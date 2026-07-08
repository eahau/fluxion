/**
 * Plugin SPI that lets contributors ship groups of related [WorkflowFunction]
 * implementations as a single discoverable unit.
 *
 * Discovery is intentionally dual-path so components work identically in
 * Spring applications and lean JVM deployments:
 * 1. **Spring environment** – any bean implementing [FunctionComponent] is
 *    picked up by `FunctionComponentAutoConfiguration` and handed to the
 *    shared registrar. Function classes returned by [functionClasses] are
 *    instantiated via the Spring-aware `FunctionInstanceProvider` so they
 *    can `@Inject` collaborators normally.
 * 2. **Non-Spring environment** – lookup goes through the standard JDK
 *    `ServiceLoader` using the descriptor at
 *    `META-INF/services/com.fluxion.core.function.FunctionComponent`.
 *    Instances in this case get no DI; collaborators must be constructed
 *    manually inside [initialize].
 *
 * Component authors implement one of:
 * * [functions] for stateless helpers that can be constructed eagerly.
 * * [functionClasses] for functions that need dependency injection or
 *   per-environment configuration.
 *
 * The builtin module exposes a `BuiltinFunction` component implementing this
 * interface; external RPC transports (Dubbo/gRPC/HTTP) also use it to
 * register their generic transport adapters.
 */
package com.fluxion.core.function

/**
 * Discoverable provider that feeds one or more [WorkflowFunction] instances
 * into the shared registry at bootstrap time.
 */
interface FunctionComponent {

    /**
     * Human-readable component identifier surfaced in logs and admin metrics.
     */
    fun componentName(): String

    /**
     * Returns eagerly-constructed function instances to register.
     *
     * Use this path when the functions have zero or trivial dependencies
     * (pure helpers, static configuration). The default implementation
     * returns an empty list – components normally override exactly one of
     * [functions] or [functionClasses].
     */
    fun functions(): List<WorkflowFunction<*>> = emptyList()

    /**
     * Returns function classes the DI-aware loader should instantiate.
     *
     * Required when functions need collaborators injected through a DI
     * container (Spring/Guice/...). The
     * [com.fluxion.di.FunctionInstanceProvider] SPI handles wiring; in a
     * Spring deployment the auto-configuration module bridges to
     * `ApplicationContext.getBean(...)`.
     */
    fun functionClasses(): List<Class<out WorkflowFunction<*>>> = emptyList()

    /**
     * Invoked once after discovery before any functions are registered.
     *
     * Receives an opaque environment map so non-Spring deployments can pass
     * config file paths, feature flags, etc. Spring-based components
     * normally ignore this because they rely on `@Value` injection instead.
     *
     * @param config environment key/value pairs supplied by the bootstrapper
     */
    fun initialize(config: Map<String, Any> = emptyMap()) {}
}
