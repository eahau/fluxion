package com.fluxion.admin.config

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.value.FunctionMeta
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component
import java.util.ServiceLoader

/**
 * Bootstraps pluggable `FunctionComponent` implementations into the admin-side
 * `FunctionRegistry` once all Spring singletons have been constructed.
 *
 * Discovery order:
 *   1. Spring-managed `FunctionComponent` beans injected via the constructor.
 *   2. `ServiceLoader`-discovered components on the classpath (de-duplicated against
 *      the Spring-discovered set so the same class never runs twice).
 *
 * Each component may contribute functions either by returning pre-built `WorkflowFunction`
 * instances or by declaring function classes that are instantiated via the optional
 * `FunctionInstanceProvider` DI bridge (falling back to reflection when the bridge is
 * absent). Duplicate function names are skipped with a warning because built-in functions
 * are not meant to be overridden.
 */
@Component
class FunctionComponentConfiguration(
    private val registry: FunctionRegistry,
    private val components: List<FunctionComponent>,
    private val instanceProvider: FunctionInstanceProvider? = null
) : SmartInitializingSingleton {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterSingletonsInstantiated() {
        val springClasses = components.map { it::class }.toSet()
        val spiComponents = ServiceLoader.load(FunctionComponent::class.java)
            .filter { it::class !in springClasses }

        (components + spiComponents).forEach { component ->
            runCatching { component.initialize(emptyMap()) }
                .onFailure { log.warn(it) { "FunctionComponent[${component.componentName()}] initialize failed" } }

            component.functions().forEach { function ->
                val meta = FunctionMeta.of(function.functionName)
                register(meta.functionName, meta, function, "component [${component.componentName()}]")
            }

            component.functionClasses().forEach { functionClass ->
                val function = instanceProvider?.getInstance(functionClass)
                    ?: functionClass.getDeclaredConstructor().newInstance()
                val meta = FunctionMeta.of(function.functionName)
                register(meta.functionName, meta, function, "component [${component.componentName()}] class [${functionClass.name}]")
            }
        }
    }

    private fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>, source: String) {
        if (registry.contains(name)) {
            log.warn { "Function [$name] from $source skipped - name already registered (builtin functions cannot be overridden)" }
            return
        }
        registry.register(name, meta, function)
        log.info { "Registered function [$name] from $source" }
    }
}
