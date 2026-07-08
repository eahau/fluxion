package com.fluxion.di

/**
 * SPI used by dynamic / scripting function hosts to resolve beans from the
 * enclosing application container.
 *
 * Typical consumers:
 *  - Groovy / JS function bodies that need access to Spring `@Service`s.
 *  - Admin console UI that wants to list available `DataSource` beans.
 *
 * Implementations are pluggable: the `fluxion-di-spring` submodule provides a
 * Spring [ApplicationContext]-backed resolver; tests and other DI frameworks
 * can register their own bean.
 */
interface DependencyResolver {

    /**
     * Look up a bean by name (e.g. Spring bean name).
     *
     * @return the resolved bean, or `null` if no bean is registered under `name`
     */
    fun resolve(name: String): Any?

    /**
     * Look up the unique bean assignable to `type`.
     *
     * If multiple beans match `type` the resolver **must** return one of them
     * deterministically (typically the first registration) and should log a
     * warning to aid debugging.
     *
     * @return the resolved bean, or `null` if no bean of `type` is registered
     */
    fun <T> resolveByType(type: Class<T>): T?
}
