package com.fluxion.core.engine

import com.fluxion.cache.FluxionCacheFactory
import com.fluxion.core.value.EngineResult
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration

class CaffeineIdempotencyStore : IdempotencyStore {

    @Volatile
    private var settings: IdempotencyCacheSettings = IdempotencyCacheSettings(
        enabled = true,
        ttlHours = DEFAULT_TTL_HOURS
    )

    private val cache: Cache<String, CachedExecution> = FluxionCacheFactory.get("idempotency")

    override fun get(key: String, workflowId: String?): CachedExecution? {
        if (!settings.effective(workflowId).enabled) return null
        return cache.getIfPresent(key)?.takeIf { !it.isExpired() }
    }

    override fun put(key: String, result: EngineResult, workflowId: String) {
        val effective = settings.effective(workflowId)
        if (!effective.enabled) return
        val ttl = Duration.ofHours(effective.ttlHours)
        cache.put(key, CachedExecution.from(result, workflowId, ttl))
    }

    fun apply(settings: IdempotencyCacheSettings) {
        this.settings = settings
    }

    companion object {
        private const val DEFAULT_TTL_HOURS = 24L
        private const val DEFAULT_MAX_SIZE = 1_000L
    }
}