package com.fluxion.decorator.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process, single-JVM [RateLimitStore] implementation built on top of
 * [ConcurrentHashMap.compute] for atomicity.
 *
 * Suitable for single-instance deployments and unit tests. Replace with a
 * Redis-backed implementation in any horizontally scaled setup.
 */
class LocalRateLimitStore : RateLimitStore {

    private val states = ConcurrentHashMap<String, SlidingWindowState>()

    override fun tryAcquire(key: String, config: RateLimitConfig): Boolean {
        val now = System.currentTimeMillis()
        val allowed = AtomicBoolean(false)
        // compute() is atomic per key: this serialises concurrent attempts
        // to acquire the same bucket without locking globally.
        states.compute(key) { _, existing ->
            val (state, ok) = existing?.tryAcquire(config, now)
                ?: (SlidingWindowState.initial(now) to true)
            allowed.set(ok)
            state
        }
        return allowed.get()
    }
}
