package com.fluxion.decorator.impl.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.decorator.ratelimit.SlidingWindowState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SlidingWindowStateTest {

    private val config = RateLimitConfig(upLimited = 3, cdSeconds = 10, recoveryPerCd = 3)

    @Test
    fun `initial state should consume one permit`() {
        val now = 1_000_000L
        val state = SlidingWindowState.initial(now)
        assertEquals(1L, state.used)
        assertEquals(now, state.lastAcquireTimeMs)
        assertEquals(0L, state.fractionalPermitsNumerator)
    }

    @Test
    fun `should allow requests while below limit`() {
        val now = 1_000_000L
        val state = SlidingWindowState.initial(now)

        val (s1, ok1) = state.tryAcquire(config, now + 1)
        assertTrue(ok1)
        assertEquals(2L, s1.used)

        val (s2, ok2) = s1.tryAcquire(config, now + 2)
        assertTrue(ok2)
        assertEquals(3L, s2.used)
    }

    @Test
    fun `should deny requests when limit reached`() {
        val now = 1_000_000L
        val state = SlidingWindowState(3, now, 0)

        val (next, ok) = state.tryAcquire(config, now + 1)
        assertFalse(ok)
        assertEquals(now + 1, next.lastAcquireTimeMs)
    }

    @Test
    fun `should recover permits after cooldown`() {
        val now = 1_000_000L
        val state = SlidingWindowState(3, now, 0)

        // After one full cd (10s), 3 permits recovered => allow and reset used to 1
        val (next, ok) = state.tryAcquire(config, now + 10_000)
        assertTrue(ok)
        assertEquals(1L, next.used)
        assertEquals(0L, next.fractionalPermitsNumerator)
    }

    @Test
    fun `should cap recovered permits at upLimited`() {
        val now = 1_000_000L
        val state = SlidingWindowState(3, now, 0)

        // After many cds, at most upLimited permits are recovered
        val (next, ok) = state.tryAcquire(config, now + 1_000_000)
        assertTrue(ok)
        assertEquals(1L, next.used)
    }

    @Test
    fun `encode and decode should roundtrip`() {
        val state = SlidingWindowState(5, 123456789L, 42L)
        val decoded = SlidingWindowState.decode(state.encode())
        assertEquals(state, decoded)
    }
}
