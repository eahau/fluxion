package com.fluxion.runtime.core.router

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.engine.CachedExecution
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * 幂等路由器 — WorkflowRouter 装饰器
 *
 * 从 [UnifiedRequest.headers] 中提取 `X-Idempotency-Key`：
 *   - 命中缓存 → 直接返回缓存结果，不执行工作流
 *   - 未命中 → 委托内部路由器执行，执行完成后缓存结果
 *   - 未携带 header → 透传，不做幂等处理
 *
 * 线程安全：
 *   并发相同 key 的请求时，可能存在短暂的重复执行（cache stampede），
 *   这对工作流场景是可接受的（最终一致性），如需严格互斥请在上层加分布式锁。
 */
class IdempotencyRouter(
    private val delegate: WorkflowRouter,
    private val store: IdempotencyStore
) : WorkflowRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 幂等键 header 名称 */
        const val HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key"
    }

    override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking { executeSuspend(request) }

    override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
        val key = request.headers[HEADER_IDEMPOTENCY_KEY]
        if (key.isNullOrBlank()) {
            return delegate.executeSuspend(request)
        }

        val workflowId = request.workflowId ?: request.protocol

        // 1. 查询缓存（按工作流维度定位缓存策略）
        val cached = store.get(key, workflowId)
        if (cached != null) {
            log.debug("Idempotency cache HIT key=$key workflow=$workflowId executionId=${cached.executionId}")
            return cached.toResponse()
        }

        // 2. 执行
        log.debug("Idempotency cache MISS key=$key workflow=$workflowId, executing workflow")
        val response = delegate.executeSuspend(request)

        // 3. 缓存结果（仅缓存成功结果，失败结果不缓存，允许重试）
        if (response.success) {
            try {
                store.put(key, response.toEngineResult(), workflowId)
            } catch (ex: Exception) {
                log.warn("Failed to cache idempotency result key=$key workflow=$workflowId", ex)
            }
        }

        return response
    }

    // ─── 转换辅助 ─────────────────────────────────────────────────

    private fun CachedExecution.toResponse() = UnifiedResponse(
        success = success,
        data = data,
        executionId = executionId,
        errorMsg = errorMsg
    )

    private fun UnifiedResponse.toEngineResult() = EngineResult(
        success = success,
        data = data,
        executionId = executionId,
        errorMsg = errorMsg
    )
}
