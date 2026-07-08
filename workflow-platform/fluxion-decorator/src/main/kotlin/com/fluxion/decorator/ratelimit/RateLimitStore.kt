package com.fluxion.decorator.ratelimit

/**
 * 闂勬劖绁﹂悩鑸碘偓浣哥摠閸?SPI 閳?鐏炲繗鏂€閺堫剙婀撮崘鍛摠 / Redis 缁涘鐤勯悳鏉挎▕瀵倶鈧? *
 * 鐎圭偟骞囩猾濠氭付娣囨繆鐦?[tryAcquire] 閻ㄥ嫧鈧粏顕?閸?閸愭瑢鈧繃鏆ｆ担鎾冲斧鐎涙劖鈧嶇礉闁灝鍘ら獮璺哄絺鐡掑懎褰傞妴? */
interface RateLimitStore {

    /**
     * 鐏忔繆鐦懢宄板絿娑撯偓娑擃亪妾哄ù浣筋啅閸欘垬鈧?     *
     * @param key    闂勬劖绁︾紒鏉戝闁款噯绱欐俊?nodeId閵嗕椒绗熼崝锟犳暛缁涘绱?     * @param config 闂勬劖绁﹂柊宥囩枂
     * @return true 鐞涖劎銇氶弨鎹愵攽閿涘牆鍑″☉鍫ｂ偓妞剧娑擃亙鎶ら悧宀嬬礆閿涘畺alse 鐞涖劎銇氱悮顐︽濞?     */
    fun tryAcquire(key: String, config: RateLimitConfig): Boolean
}
