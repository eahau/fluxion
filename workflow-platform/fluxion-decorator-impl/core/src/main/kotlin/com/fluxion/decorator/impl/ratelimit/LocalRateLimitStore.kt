package com.fluxion.decorator.impl.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地内存滑动窗口限流存储。
 *
 * 基于 [ConcurrentHashMap.compute] 实现单 JVM 内的原子更新，适用于单实例或测试场景。
 * 多实例部署时请替换为 [com.fluxion.redis.ratelimit.RedisRateLimitStore] 等分布式实现。
 */
class LocalRateLimitStore : RateLimitStore {

    private val states = ConcurrentHashMap<String, SlidingWindowState>()

    override fun tryAcquire(key: String, config: RateLimitConfig): Boolean {
        val now = System.currentTimeMillis()
        val allowed = AtomicBoolean(false)
        states.compute(key) { _, existing ->
            val (state, ok) = existing?.tryAcquire(config, now) ?: (SlidingWindowState.initial(now) to true)
            allowed.set(ok)
            state
        }
        return allowed.get()
    }
}
