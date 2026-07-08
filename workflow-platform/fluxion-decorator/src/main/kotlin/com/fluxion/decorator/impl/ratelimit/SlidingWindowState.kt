package com.fluxion.decorator.impl.ratelimit

import com.fluxion.decorator.ratelimit.RateLimitConfig

/**
 * 婊戝姩绐楀彛闄愭祦鍣ㄧ姸鎬?鈥?涓嶅彲鍙樺€煎璞°€?
 *
 * 缂栫爜鏍煎紡锛歶sed:lastAcquireTimeMs:fractionalPermitsNumerator
 */
data class SlidingWindowState(
    val used: Long,
    val lastAcquireTimeMs: Long,
    val fractionalPermitsNumerator: Long
) {

    /**
     * 鍦ㄥ綋鍓嶇姸鎬佷笅灏濊瘯鑾峰彇涓€涓护鐗屻€?
     *
     * @return 鏂扮姸鎬佷笌鏄惁鏀捐鐨勪簩鍏冪粍
     */
    fun tryAcquire(config: RateLimitConfig, now: Long): Pair<SlidingWindowState, Boolean> {
        if (used < config.upLimited) {
            return copy(used = used + 1, lastAcquireTimeMs = now) to true
        }

        val pastTimeMs = (now - lastAcquireTimeMs).coerceAtLeast(0)
        val cdMs = config.cdSeconds * 1000L
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

    fun encode(): String = "$used:$lastAcquireTimeMs:$fractionalPermitsNumerator"

    companion object {
        fun initial(now: Long): SlidingWindowState = SlidingWindowState(1, now, 0)

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
