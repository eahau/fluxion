package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * Global registry of [FunctionInstanceProvider] implementations.
 *
 * The engine calls [getProvider] when a [WorkflowFunction] class is first
 * encountered; providers are consulted in **registration order** and the
 * first one whose `supports()` returns `true` wins.
 *
 * This allows non-Spring environments (script engines, unit tests, Guice
 * deployments) to plug in their own DI strategy without modifying the
 * engine code. Spring environments use the dedicated Spring submodule which
 * auto-registers its provider at startup.
 */
object FunctionInstanceProviderRegistry {

    private val providers = mutableListOf<FunctionInstanceProvider>()

    /**
     * Append `provider` to the end of the resolution chain.
     *
     * Newly registered providers take precedence over pre-existing ones only
     * if their `supports()` is strictly more specific.
     */
    fun register(provider: FunctionInstanceProvider) {
        providers.add(provider)
    }

    /**
     * Find the first registered provider that claims to support
     * `functionClass`, or `null` if none matches.
     */
    fun getProvider(functionClass: Class<out WorkflowFunction<*>>): FunctionInstanceProvider? {
        return providers.firstOrNull { it.supports(functionClass) }
    }

    /**
     * Remove all currently registered providers. Used primarily by the test
     * harness to isolate runs from each other.
     */
    fun clear() {
        providers.clear()
    }
}
