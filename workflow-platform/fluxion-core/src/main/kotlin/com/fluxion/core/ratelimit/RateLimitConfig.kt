package com.fluxion.core.ratelimit

/**
 * 滑动窗口限流配置。
 *
 * @param upLimited     窗口内最大请求数（上限）
 * @param cdSeconds     恢复周期（秒）
 * @param recoveryPerCd 每个恢复周期恢复的令牌数，默认等于 [upLimited]
 */
data class RateLimitConfig(
    val upLimited: Int,
    val cdSeconds: Int,
    val recoveryPerCd: Int = upLimited
) {
    init {
        require(upLimited >= 1) { "upLimited must be >= 1, got $upLimited" }
        require(cdSeconds >= 1) { "cdSeconds must be >= 1, got $cdSeconds" }
        require(recoveryPerCd >= 1) { "recoveryPerCd must be >= 1, got $recoveryPerCd" }
    }

    companion object {
        const val DEFAULT_UP_LIMITED = 6
        const val DEFAULT_CD_SECONDS = 10
        const val DEFAULT_RECOVERY_PER_CD = DEFAULT_UP_LIMITED
    }
}
