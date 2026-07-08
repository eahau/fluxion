package com.fluxion.decorator.impl.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LocalRateLimitStoreTest {

    private val store = LocalRateLimitStore()
    private val config = RateLimitConfig(upLimited = 2, cdSeconds = 1, recoveryPerCd = 2)

    @Test
    fun `should allow requests up to limit`() {
        val key = "test:limit"
        assertTrue(store.tryAcquire(key, config))
        assertTrue(store.tryAcquire(key, config))
        assertFalse(store.tryAcquire(key, config))
    }

    @Test
    fun `should recover after cooldown`() {
        val key = "test:recovery"
        assertTrue(store.tryAcquire(key, config))
        assertTrue(store.tryAcquire(key, config))
        assertFalse(store.tryAcquire(key, config))

        Thread.sleep(1_100)
        assertTrue(store.tryAcquire(key, config))
    }

    @Test
    fun `different keys should be isolated`() {
        assertTrue(store.tryAcquire("key:a", config))
        assertTrue(store.tryAcquire("key:a", config))
        assertTrue(store.tryAcquire("key:b", config))
        assertTrue(store.tryAcquire("key:b", config))
        assertFalse(store.tryAcquire("key:a", config))
        assertFalse(store.tryAcquire("key:b", config))
    }
}
