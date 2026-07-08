package com.fluxion.config.core

/**
 * 閰嶇疆瑙ｆ瀽 + 鏍￠獙鐨勭粺涓€缁撴灉绫诲瀷 鈥?涓夊厓瀵嗗皝绫?
 *
 * 鐢ㄤ簬鍦?[AbstractKeyedConfigSubscriber] 鐨?resolve 绠＄嚎涓樉寮忚〃杈撅細
 * - [Success]锛氳В鏋?+ 鏍￠獙鍧囬€氳繃锛孾snapshot] 鍙洿鎺ヤ娇鐢?
 * - [ParseFailed]锛欽SON/鏍煎紡瑙ｆ瀽澶辫触锛孾error] 鎼哄甫寮傚父淇℃伅
 * - [ValidationFailed]锛氳В鏋愭垚鍔熶絾涓氬姟鏍￠獙鏈€氳繃锛孾reason] 鎼哄甫鎷掔粷鍘熷洜
 *
 * 璋冪敤鏂规牴鎹粨鏋滃喅瀹氬悗缁瓥鐣ワ細浣跨敤銆侀檷绾у埌涓婁竴涓ソ鍊笺€佹垨璺宠繃銆?
 */
sealed class ConfigResolveResult<out T> {

    /** 瑙ｆ瀽 + 鏍￠獙鍧囬€氳繃 */
    data class Success<T>(val snapshot: T) : ConfigResolveResult<T>()

    /** JSON / 鏍煎紡瑙ｆ瀽澶辫触 */
    data class ParseFailed(val error: Exception) : ConfigResolveResult<Nothing>()

    /** 瑙ｆ瀽鎴愬姛浣嗕笟鍔℃牎楠屾湭閫氳繃 */
    data class ValidationFailed(val reason: String) : ConfigResolveResult<Nothing>()
}
