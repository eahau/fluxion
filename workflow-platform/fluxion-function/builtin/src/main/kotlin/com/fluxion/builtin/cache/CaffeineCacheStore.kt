package com.fluxion.builtin.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.fluxion.decorator.decorator.CacheStore
import java.util.concurrent.TimeUnit

/**
 * Default in-process [CacheStore] backed by Caffeine.
 *
 * - Zero external dependencies — works in unit tests and lightweight deployments.
 * - Per-entry TTL via Caffeine's variable-expiry API, so the decorator layer can
 *   set individual TTLs per key without relying on fixed `expireAfterWrite`.
 * - Deployments needing a shared, distributed cache (Redis / Ignite / ...)
 *   supply their own `CacheStore` bean and it takes precedence via the
 *   `@ConditionalOnMissingBean` wiring in BuiltinConfig.
 */
class CaffeineCacheStore(
    maximumSize: Long = DEFAULT_MAX_SIZE
) : CacheStore {

    private data class CacheEntry(
        val value: Any?,
        val ttlNanos: Long
    )

    private val cache = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfter(object : Expiry<String, CacheEntry> {
            override fun expireAfterCreate(
                key: String,
                value: CacheEntry,
                currentTime: Long
            ): Long = value.ttlNanos.coerceAtLeast(0)

            override fun expireAfterUpdate(
                key: String,
                value: CacheEntry,
                currentTime: Long,
                currentDuration: Long
            ): Long = value.ttlNanos.coerceAtLeast(0)

            override fun expireAfterRead(
                key: String,
                value: CacheEntry,
                currentTime: Long,
                currentDuration: Long
            ): Long = currentDuration
        })
        .build<String, CacheEntry>()

    override fun get(key: String): Any? = cache.getIfPresent(key)?.value

    override fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit) {
        cache.put(key, CacheEntry(value, unit.toNanos(ttl)))
    }

    override fun evict(key: String) {
        cache.invalidate(key)
    }

    companion object {
        private const val DEFAULT_MAX_SIZE = 10_000L
    }
}
