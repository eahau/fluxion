package com.fluxion.script.util

import com.fluxion.di.DependencyResolver

/**
 * Read-only `Map<String, Any?>` view backed by a [DependencyResolver].
 *
 * Exposed inside Groovy scripts as the implicit variable `beans` so workflow
 * authors can access any Spring/DI-managed bean by name:
 * ```groovy
 * def userSvc = beans.userService
 * def orderSvc = beans['orderService']
 * ```
 *
 * Lookups are lazy and delegate directly to [DependencyResolver.resolve].
 * Mutating operations (`entries`, `put`, `remove`, `clear`) are unsupported —
 * the DI container owns bean lifecycle, scripts may only observe/invoke.
 */
class BeanNameMap(
    private val resolver: DependencyResolver
) : AbstractMap<String, Any?>() {

    /** Resolve a bean by name; returns null when no bean is registered. */
    override fun get(key: String): Any? = resolver.resolve(key)

    /** Listing all beans is intentionally unsupported — we are a lookup proxy, not a registry snapshot. */
    override val entries: Set<Map.Entry<String, Any?>>
        get() = throw UnsupportedOperationException("beans is read-only proxy")

    /** Presence check backed by resolver; `containsKey` returns true iff resolve returns a non-null instance. */
    override fun containsKey(key: String): Boolean = resolver.resolve(key) != null
}
