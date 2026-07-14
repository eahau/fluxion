package com.fluxion.core.ratelimit

interface RateLimitStore {
    fun tryAcquire(key: String, config: RateLimitConfig): Boolean
}