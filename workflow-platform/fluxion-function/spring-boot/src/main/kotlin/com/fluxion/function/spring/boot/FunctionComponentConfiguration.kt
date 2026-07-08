package com.fluxion.function.spring.boot

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import java.util.ServiceLoader

/**
 * Discovers and registers [FunctionComponent] instances into the global [FunctionRegistry].
 *
 * Scans two sources for components to avoid double-registration:
 * 1. Spring-managed beans (injected via constructor, usually @Component/@Bean)
 * 2. Java ServiceLoader entries from META-INF/services (plain JVM classes without Spring)
 *
 * Implements [SmartInitializingSingleton] to defer registration until ALL singleton beans
 * are fully constructed—this guarantees circular-dependency safety and that any
 * post-processors (e.g. BuiltinConfig, RedisWorkflowAutoConfiguration) have already
 * contributed their beans.
 *
 * Each component contributes zero or more functions via two paths:
 * - Direct `WorkflowFunction` instances returned by [FunctionComponent.functions]
 * - Function classes returned by [FunctionComponent.functionClasses] (instantiated via
 *   the optional [FunctionInstanceProvider] for DI, or reflective no-arg constructor as
 *   fallback).
 *
 * Collaborates with [FunctionComponentAutoConfiguration] which wires this as a bean.
 */
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
            component.initialize(emptyMap())

            component.functions().forEach { function ->
                val name = function.functionName
                register(name, function, "component [${component.componentName()}]")
            }

            component.functionClasses().forEach { functionClass ->
                val function = instanceProvider?.getInstance(functionClass)
                    ?: functionClass.getDeclaredConstructor().newInstance()
                val name = function.functionName
                register(name, function, "component [${component.componentName()}] class [${functionClass.name}]")
            }
        }
    }

    private fun register(name: String, function: WorkflowFunction<*>, source: String) {
        if (registry.contains(name)) {
            // Built-in functions take precedence; custom code cannot shadow them to
            // prevent subtle breakage when upgrading the platform.
            log.warn { "Function [$name] from $source skipped — name already registered (builtin functions cannot be overridden)" }
            return
        }
        registry.register(name, function)
        log.info { "Registered function [$name] from $source" }
    }
}
