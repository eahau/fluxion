package com.fluxion.decorator.impl.ratelimit

import com.fluxion.decorator.ratelimit.RateLimitConfig
import com.fluxion.decorator.ratelimit.RateLimitStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 鏈湴鍐呭瓨婊戝姩绐楀彛闄愭祦瀛樺偍銆?
 *
 * 鍩轰簬 [ConcurrentHashMap.compute] 瀹炵幇鍗?JVM 鍐呯殑鍘熷瓙鏇存柊锛岄€傜敤浜庡崟瀹炰緥鎴栨祴璇曞満鏅€?
 * 澶氬疄渚嬮儴缃叉椂璇锋浛鎹负 [com.fluxion.redis.ratelimit.RedisRateLimitStore] 绛夊垎甯冨紡瀹炵幇銆?
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
