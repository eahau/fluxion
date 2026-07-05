package com.fluxion.core.engine

import com.fluxion.core.value.EngineResult
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.CaffeineSpec
import com.github.benmanes.caffeine.cache.Expiry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 Caffeine 的 IdempotencyStore 内存实现，支持运行期动态调整配置。
 *
 * 支持应用级默认配置 + 工作流级覆盖的二维缓存策略：
 * - 每个工作流拥有独立的 Caffeine 缓存实例
 * - 工作流级未指定字段继承应用级默认值
 * - 配置变更时按工作流粒度热替换缓存实例并迁移已有数据
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

    /** 工作流 ID → 缓存实例，空字符串表示应用级默认缓存 */
    private val caches = ConcurrentHashMap<String, CacheHolder>()

    // ─── SPI 实现 ──────────────────────────────────────────────────────────

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

    // ─── 动态配置入口 ───────────────────────────────────────────────────────

    /**
     * 运行期热更新缓存配置。
     *
     * - 应用级配置变更：影响所有未覆盖该字段的工作流缓存
     * - 工作流级配置变更：仅影响对应工作流的缓存
     * - 工作流被禁用：清空并移除对应缓存
     * - maxSize / spec 变更：重建对应缓存实例并迁移数据
     * - TTL 变更：重建时生效，新写入条目使用新 TTL
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

    // ─── 内部 ──────────────────────────────────────────────────────────────

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
