package com.fluxion.decorator.ratelimit

/**
 * Rate-limit permit storage SPI -- decouples the `ratelimit:slidingWindow`
 * decorator from the backing store (in-memory vs Redis).
 *
 * Implementations must be thread-safe: the same key is hammered concurrently
 * by all worker threads / instances.
 */
interface RateLimitStore {

    /**
     * Atomically attempt to consume one permit for `key`.
     *
     * @param key    scoped rate-limit bucket identifier (already namespaced by
     *               appGroup + domain + prefix)
     * @param config cooldown / permit-count parameters for the bucket
     * @return `true` if a permit was granted, `false` if the bucket is exhausted
     */
    fun tryAcquire(key: String, config: RateLimitConfig): Boolean
}
