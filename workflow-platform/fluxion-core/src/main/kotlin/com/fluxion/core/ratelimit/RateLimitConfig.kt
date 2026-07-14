package com.fluxion.core.ratelimit

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