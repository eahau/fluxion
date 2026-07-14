package com.fluxion.di

import com.fluxion.core.function.WorkflowFunction

/**
 * DI-agnostic SPI for instantiating [WorkflowFunction] implementations.
 *
 * Allows the engine to delegate function instance creation to the host
 * application's container (Spring, Guice, Koin, ...) so that constructor
 * injection, field injection and AOP proxies behave consistently with the
 * rest of the codebase.
 *
 * Providers are registered globally with [FunctionInstanceProviderRegistry];
 * when the engine needs an instance it walks the registry and calls
 * [getInstance] on the first entry whose [supports] returns `true`.
 */
interface FunctionInstanceProvider {

    /**
     * Return `true` if this provider can create instances of `functionClass`.
     *
     * The Spring provider always returns `true` (catch-all); more specific
     * providers (e.g. a Groovy-script provider) should return `true` only
     * for classes they own.
     */
    fun supports(functionClass: Class<*>): Boolean

    /**
     * Obtain an instance of `functionClass`, fully wired by the DI container.
     *
     * Callers may assume the returned instance is safe to cache across
     * invocations -- i.e. if the underlying container returns a singleton,
     * the engine sees the same instance every time.
     */
    fun getInstance(functionClass: Class<out WorkflowFunction<*>>): WorkflowFunction<*>
}
