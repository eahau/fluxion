package com.fluxion.core.ratelimit

/**
 * 限流状态存储 SPI — 屏蔽本地内存 / Redis 等实现差异。
 *
 * 实现类需保证 [tryAcquire] 的“读-判-写”整体原子性，避免并发超发。
 */
interface RateLimitStore {

    /**
     * 尝试获取一个限流许可。
     *
     * @param key    限流维度键（如 nodeId、业务键等）
     * @param config 限流配置
     * @return true 表示放行（已消耗一个令牌），false 表示被限流
     */
    fun tryAcquire(key: String, config: RateLimitConfig): Boolean
}
