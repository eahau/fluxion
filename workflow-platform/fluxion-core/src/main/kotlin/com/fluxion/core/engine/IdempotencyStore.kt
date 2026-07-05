package com.fluxion.core.engine

import com.fluxion.core.value.EngineResult

/**
 * 幂等执行结果存储 SPI — 防止同一请求被重复执行
 *
 * 使用场景：
 *   调用方在请求头中携带 `X-Idempotency-Key`，引擎在执行前查询此存储：
 *   - 命中 → 直接返回缓存结果，跳过执行
 *   - 未命中 → 正常执行，执行完成后写入缓存
 *
 * 典型实现：
 *   - InMemoryIdempotencyStore（测试 / 单实例）
 *   - RedisIdempotencyStore（分布式集群，推荐 TTL 24h）
 *   - DbIdempotencyStore（持久化审计场景）
 *
 * 默认实现：NoopIdempotencyStore（不缓存，等效于关闭幂等）
 */
interface IdempotencyStore {

    /**
     * 查询已缓存的执行结果
     *
     * @param key 幂等键（由调用方生成，通常为 UUID）
     * @param workflowId 工作流 ID，用于定位工作流级缓存策略；null 时使用应用级默认策略
     * @return 缓存的 [CachedExecution]，未命中返回 null
     */
    fun get(key: String, workflowId: String? = null): CachedExecution?

    /**
     * 缓存执行结果
     *
     * @param key    幂等键
     * @param result 执行结果
     * @param workflowId 工作流 ID
     */
    fun put(key: String, result: EngineResult, workflowId: String)

    companion object {
        /** 空实现：不缓存，幂等功能关闭 */
        val NOOP: IdempotencyStore = object : IdempotencyStore {
            override fun get(key: String, workflowId: String?): CachedExecution? = null
            override fun put(key: String, result: EngineResult, workflowId: String) {}
        }
    }
}

/**
 * 幂等缓存条目
 *
 * @param success     是否执行成功
 * @param data        响应数据
 * @param executionId 执行 ID
 * @param errorMsg    错误信息（失败时）
 * @param workflowId  工作流 ID
 * @param cachedAt    缓存时间戳（毫秒）
 */
data class CachedExecution(
    val success: Boolean,
    val data: Any?,
    val executionId: String?,
    val errorMsg: String?,
    val workflowId: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 从引擎结果创建缓存条目 */
        @JvmStatic
        fun from(result: EngineResult, workflowId: String) = CachedExecution(
            success = result.success,
            data = result.data,
            executionId = result.executionId,
            errorMsg = result.errorMsg,
            workflowId = workflowId
        )
    }
}
