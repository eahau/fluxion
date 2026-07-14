package com.fluxion.decorator.ratelimit

import com.fluxion.core.ratelimit.RateLimitConfig

/**
 * Immutable state of a sliding-window rate limit bucket.
 *
 * ### Encoding
 * The state is intentionally compact and serialises as a colon-delimited
 * triple `used:lastAcquireTimeMs:fractionalPermitsNumerator` which stores
 * cleanly in a Redis `STRING` and preserves millisecond-level precision.
 *
 * The fractional-permits field avoids cumulative rounding drift across
 * repeated partial-recovery periods: it carries the integer numerator of
 * the fraction `(pastMs * recoveryPerCd) / cdMs` so the remainder carries
 * over to the next acquire instead of being truncated.
 */
data class SlidingWindowState(
    /** Permits currently consumed in this window. */
    val used: Long,
    /** Wall-clock ms of the most recent `tryAcquire` call (win or lose). */
    val lastAcquireTimeMs: Long,
    /** Numerator of unrecovered fractional permits; denominator is `cdSeconds*1000`. */
    val fractionalPermitsNumerator: Long
) {

    /**
     * Try to consume one permit, returning the new state and whether the
     * request was admitted.
     *
     * This function is pure: callers are responsible for publishing the
     * returned state atomically (the store layer does this via
     * `ConcurrentHashMap.compute` / Redis atomic scripts).
     */
    fun tryAcquire(config: RateLimitConfig, now: Long): Pair<SlidingWindowState, Boolean> {
        if (used < config.upLimited) {
            return copy(used = used + 1, lastAcquireTimeMs = now) to true
        }

        val pastTimeMs = (now - lastAcquireTimeMs).coerceAtLeast(0)
        val cdMs = config.cdSeconds * 1000L
        // Fractional recovery is accumulated as a single numerator to avoid
        // repeated float/double rounding errors across many short intervals.
        val totalNumerator = pastTimeMs * config.recoveryPerCd + fractionalPermitsNumerator
        val permits = minOf(config.upLimited.toLong(), totalNumerator / cdMs)

        return if (permits >= 1) {
            val nextUsed = config.upLimited - permits + 1
            copy(
                used = nextUsed.coerceAtLeast(1L).coerceAtMost(config.upLimited.toLong()),
                lastAcquireTimeMs = now,
                fractionalPermitsNumerator = totalNumerator % cdMs
            ) to true
        } else {
            copy(
                lastAcquireTimeMs = now,
                fractionalPermitsNumerator = totalNumerator % cdMs
            ) to false
        }
    }

    /** Serialise this state to the standard `used:last:fractional` form. */
    fun encode(): String = "$used:$lastAcquireTimeMs:$fractionalPermitsNumerator"

    companion object {
        /** Fresh state: one permit already consumed, no fractional carry. */
        fun initial(now: Long): SlidingWindowState = SlidingWindowState(1, now, 0)

        /** Parse a state previously produced by [encode]. */
        fun decode(value: String): SlidingWindowState {
            val sep1 = value.indexOf(':')
            require(sep1 != -1) { "Invalid rate limit state format: $value" }
            val sep2 = value.indexOf(':', sep1 + 1)
            require(sep2 != -1) { "Invalid rate limit state format: $value" }
            return SlidingWindowState(
                used = value.substring(0, sep1).toLong(),
                lastAcquireTimeMs = value.substring(sep1 + 1, sep2).toLong(),
                fractionalPermitsNumerator = value.substring(sep2 + 1).toLong()
            )
        }
    }
}
