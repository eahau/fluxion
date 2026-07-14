package com.fluxion.core.engine

import com.fluxion.core.value.EngineResult
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.CaffeineSpec
import com.github.benmanes.caffeine.cache.Expiry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Caffeine-backed [IdempotencyStore] with per-workflow cache partitioning and
 * runtime-reconfigurable TTL / size limits.
 *
 * Supports:
 *  - Global + per-workflow settings override via [apply]
 *  - Optional [CaffeineSpec] string for operators who need fine-grained eviction
 *  - Per-key TTL applied at create/update time (read does NOT refresh TTL so
 *    cached executions cannot "live forever" via hot reads)
 *  - Hot-swap of cache instances when effective settings change (existing
 *    entries are migrated so in-flight workflows don't lose idempotency)
 */
class CaffeineIdempotencyStore(
    ttl: Duration = DEFAULT_TTL,
    maxSize: Long = DEFAULT_MAX_SIZE,
    spec: String? = null
) : IdempotencyStore {

    @Volatile
    private var settings: IdempotencyCacheSettings = IdempotencyCacheSettings(
        enabled = true,
        ttlHours = ttl.toHours(),
        maxSize = maxSize,
        spec = spec
    )

    /** Workflow ID -> cache/settings pair. Empty string key is the "global" fallback. */
    private val caches = ConcurrentHashMap<String, CacheHolder>()

    // ---- SPI implementation ------------------------------------------------

    override fun get(key: String, workflowId: String?): CachedExecution? {
        val effective = settings.effective(workflowId)
        if (!effective.enabled) return null
        return cacheFor(workflowId, effective).cache.getIfPresent(key)
    }

    override fun put(key: String, result: EngineResult, workflowId: String) {
        val effective = settings.effective(workflowId)
        if (!effective.enabled) return
        cacheFor(workflowId, effective).cache.put(key, CachedExecution.from(result, workflowId))
    }

    // ---- Runtime reconfiguration -----------------------------------------

    /**
     * Replace the active settings and reconcile existing caches.
     *
     * Rules applied to every cached workflow:
     *  - If effective settings are now disabled the cache is invalidated AND
     *    removed so memory is freed immediately.
     *  - If effective settings differ from the currently held config a new
     *    Caffeine cache is built, previous entries are copied over via
     *    `putAll` (serialised to minimise lock duration), and the old
     *    holder is dropped.
     *  - If settings match no work is performed (fast path).
     */
    fun apply(settings: IdempotencyCacheSettings) {
        this.settings = settings

        val iterator = caches.iterator()
        while (iterator.hasNext()) {
            val (workflowKey, holder) = iterator.next()
            val workflowId = workflowKey.takeIf { it.isNotEmpty() }
            val effective = settings.effective(workflowId)

            if (!effective.enabled) {
                holder.cache.invalidateAll()
                iterator.remove()
                continue
            }

            if (holder.config != effective) {
                val newCache = buildCache(effective)
                newCache.putAll(holder.cache.asMap())
                caches[workflowKey] = CacheHolder(newCache, effective)
            }
        }
    }

    // ---- Internal helpers -------------------------------------------------

    private fun cacheFor(workflowId: String?, effective: EffectiveIdempotencyCacheSettings): CacheHolder {
        val workflowKey = workflowId ?: DEFAULT_WORKFLOW_KEY
        return caches.compute(workflowKey) { _, existing ->
            if (existing != null && existing.config == effective) existing
            else CacheHolder(buildCache(effective), effective)
        } ?: error("CacheHolder must not be null")
    }

    private fun buildCache(effective: EffectiveIdempotencyCacheSettings): com.github.benmanes.caffeine.cache.Cache<String, CachedExecution> {
        val ttlNanos = Duration.ofHours(effective.ttlHours).toNanos()
        val builder = if (effective.spec.isNullOrBlank()) {
            Caffeine.newBuilder()
        } else {
            Caffeine.from(CaffeineSpec.parse(effective.spec))
        }
        return builder
            .expireAfter(object : Expiry<String, CachedExecution> {
                override fun expireAfterCreate(key: String, value: CachedExecution, currentTime: Long): Long =
                    ttlNanos.coerceAtLeast(0)

                override fun expireAfterUpdate(
                    key: String, value: CachedExecution, currentTime: Long, currentDuration: Long
                ): Long = ttlNanos.coerceAtLeast(0)

                override fun expireAfterRead(
                    key: String, value: CachedExecution, currentTime: Long, currentDuration: Long
                ): Long = currentDuration
            })
            .maximumSize(effective.maxSize)
            .build<String, CachedExecution>()
    }

    private data class CacheHolder(
        val cache: com.github.benmanes.caffeine.cache.Cache<String, CachedExecution>,
        val config: EffectiveIdempotencyCacheSettings
    )

    companion object {
        private val DEFAULT_TTL: Duration = Duration.ofHours(24)
        private const val DEFAULT_MAX_SIZE = 1_000L
        private const val DEFAULT_WORKFLOW_KEY = ""
    }
}
