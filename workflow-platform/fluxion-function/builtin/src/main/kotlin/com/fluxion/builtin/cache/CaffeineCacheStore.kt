package com.fluxion.builtin.cache

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.decorator.decorator.CacheStore
import com.github.benmanes.caffeine.cache.Cache
import java.util.concurrent.TimeUnit

class CaffeineCacheStore : CacheStore {

    private data class CacheEntry(
        val value: Any?,
        val ttlNanos: Long
    )

    private val cache: Cache<String, Any> = FluxionCacheFactory.get("builtin-cache")

    @Suppress("UNCHECKED_CAST")
    override fun get(key: String): Any? {
        val entry = cache.getIfPresent(key) as CacheEntry?
        return entry?.value
    }

    override fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit) {
        cache.put(key, CacheEntry(value, unit.toNanos(ttl)))
    }

    override fun evict(key: String) {
        cache.invalidate(key)
    }
}