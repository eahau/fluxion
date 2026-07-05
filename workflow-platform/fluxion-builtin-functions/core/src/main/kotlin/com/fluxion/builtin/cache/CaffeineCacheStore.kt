package com.fluxion.builtin.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.fluxion.core.decorator.CacheStore
import java.util.concurrent.TimeUnit

/**
 * 基于 Caffeine 的 CacheStore 默认实现。
 *
 * - 本地堆缓存，零 Spring 依赖
 * - 通过 Caffeine `expireAfter(Expiry)` 支持逐键 TTL，由 Caffeine 自动淘汰
 * - 作为兜底实现，可被 RedisCacheStore 等外部实现替换
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
