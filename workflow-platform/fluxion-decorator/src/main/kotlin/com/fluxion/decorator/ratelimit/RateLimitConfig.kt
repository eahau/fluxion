package com.fluxion.decorator.ratelimit

/**
 * Immutable parameters that describe a sliding-window rate limit bucket.
 *
 * The parameters intentionally mirror the admin console UI fields; values are
 * validated at construction via `require` so bad configuration surfaces early.
 *
 * @param upLimited     maximum number of requests allowed inside the window
 * @param cdSeconds     length of the cooldown window in seconds
 * @param recoveryPerCd number of permits restored every `cdSeconds` (defaults
 *                      to `upLimited`, i.e. full reset after one cooldown)
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
