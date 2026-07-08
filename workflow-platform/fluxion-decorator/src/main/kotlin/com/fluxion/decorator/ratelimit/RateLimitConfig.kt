package com.fluxion.decorator.ratelimit

/**
 * 濠婃垵濮╃粣妤€褰涢梽鎰ウ闁板秶鐤嗛妴? *
 * @param upLimited     缁愭褰涢崘鍛付婢堆嗩嚞濮瑰倹鏆熼敍鍫滅瑐闂勬劧绱? * @param cdSeconds     閹垹顦查崨銊︽埂閿涘牏顫楅敍? * @param recoveryPerCd 濮ｅ繋閲滈幁銏狀槻閸涖劍婀￠幁銏狀槻閻ㄥ嫪鎶ら悧灞炬殶閿涘矂绮拋銈囩搼娴?[upLimited]
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
