package com.fluxion.script.config

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.config.core.FunctionConfigSubscriber
import com.fluxion.core.function.FunctionRegistry
import com.github.benmanes.caffeine.cache.Cache
import com.fluxion.outbound.OutboundTransportRegistry
import com.fluxion.di.DependencyResolver
import com.fluxion.script.groovy.GroovyScriptFunction
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * Script engine Spring Boot auto-configuration.
 *
 * Wires two related concerns into the application context:
 *
 * 1. **Built-in script engines** (top-level auto-config):
 *    Conditionally create engine singletons iff the relevant language JAR is
 *    on the classpath. Currently only Groovy (`groovy.lang.GroovyShell`) is
 *    implemented; a future JavaScript / WASM engine would add parallel
 *    `@ConditionalOnClass` beans here. Engine creation also attaches the
 *    Spring `DependencyResolver` so scripts can access DI-managed beans via
 *    `bean("...")` / `beans.xxx` bindings.
 *
 * 2. **Worker-side function hot-reload** (nested `FunctionConfigApplierConfiguration`):
 *    ONLY activates on `workflow.instance.role=worker` nodes AND when a
 *    config-center subscriber bean is present. The applier loads every
 *    `FunctionConfigSnapshot` from the subscriber, registers it into
 *    `FunctionRegistry`, then watches for push-based updates so scripts /
 *    external functions can be hot-redeployed without a rolling restart.
 */
@AutoConfiguration
class ScriptEngineAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Build the Groovy engine when the Groovy runtime is present.
     *
     * Wires in `DependencyResolver` (when available) as a script binding so
     * `bean('foo')` / `beans.foo` return live Spring beans.
     */
    @Bean
    @ConditionalOnClass(name = ["groovy.lang.GroovyShell"])
    fun groovyScriptFunction(
        @Autowired(required = false) dependencyResolver: DependencyResolver?
    ): GroovyScriptFunction {
        log.info { "Registering Groovy script engine (builtin:groovyScript)" }
        val compileCache = FluxionCacheFactory.get<Int, Class<*>>("script-compile")
        return GroovyScriptFunction(compileCache).apply {
            this.dependencyResolver = dependencyResolver
        }
    }

    /**
     * Eager registrar that publishes the built-in engine beans into the
     * shared `FunctionRegistry` so workflow DAGs can reference
     * `builtin:groovyScript` by convention.
     */
    @Bean
    fun scriptEngineRegistrar(
        registry: FunctionRegistry,
        @Autowired(required = false) groovyScriptFunction: GroovyScriptFunction?
    ): ScriptEngineRegistrar = ScriptEngineRegistrar(registry, groovyScriptFunction)
}

/**
 * Simple init-side component — on construction publishes each built-in
 * engine to the central FunctionRegistry under its canonical ref (e.g.
 * `builtin:groovyScript`). Construction-order dependencies are handled by
 * Spring's bean wiring (we don't need `@DependsOn` because the ctor args
 * already express the graph).
 */
class ScriptEngineRegistrar(
    registry: FunctionRegistry,
    groovyScriptFunction: GroovyScriptFunction?
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        var count = 0
        groovyScriptFunction?.let {
            registry.register("builtin:groovyScript", it)
            count++
        }
        log.info { "Registered $count script engine functions" }
    }
}

/**
 * Worker-only configuration for the function-config applier.
 *
 * Gated by `workflow.instance.role=worker` so control-plane nodes (admin,
 * scheduler) don't needlessly load + run scripts or wire outbound
 * transports. Also requires a [FunctionConfigSubscriber] bean to be
 * available — deployments without a config center (embedded tests, simple
 * fat-jar deployments) simply omit this bean and the configuration is
 * skipped entirely.
 */
@Configuration
@ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "worker")
@ConditionalOnBean(FunctionConfigSubscriber::class)
class FunctionConfigApplierConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Build and immediately initialise the applier.
     *
     * Side-effect construction: `init()` is invoked inside the factory method
     * so all script/external functions are registered in the registry BEFORE
     * the SmartLifecycle-managed HTTP/MQ/RPC adapters start listening for
     * traffic (prevents a brief "no function registered" window at boot).
     *
     * App-group scoping is read from `workflow.instance.app-group` so a
     * single config-center namespace can fan-out functions by fleet partition.
     */
    @Bean
    fun functionConfigApplier(
        subscriber: FunctionConfigSubscriber,
        registry: FunctionRegistry,
        @Autowired(required = false) groovyScriptFunction: GroovyScriptFunction?,
        @Autowired(required = false) transportRegistry: OutboundTransportRegistry?,
        env: Environment
    ): FunctionConfigApplier {
        val appGroup = env.getProperty("workflow.instance.app-group")

        val resolver: (String) -> String? = { ref -> subscriber.get(ref)?.scriptBody }
        groovyScriptFunction?.scriptRefResolver = resolver

        val applier = FunctionConfigApplier(
            subscriber = subscriber,
            registry = registry,
            groovyEngine = groovyScriptFunction,
            appGroup = appGroup,
            transportRegistry = transportRegistry ?: OutboundTransportRegistry()
        )
        applier.init()
        log.info { "FunctionConfigApplier initialized for appGroup=$appGroup" }
        return applier
    }
}
