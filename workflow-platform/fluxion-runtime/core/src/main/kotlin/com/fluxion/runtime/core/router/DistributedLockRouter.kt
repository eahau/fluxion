package com.fluxion.runtime.core.router

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.exception.LockAcquisitionException
import com.fluxion.core.lock.DistributedLockProvider
import kotlinx.coroutines.runBlocking
import org.slf4j.*

/**
 * 工作流级分布式锁路由器 — WorkflowRouter 装饰器。
 *
 * 在整段工作流执行前后加锁，确保同一锁键的工作流请求串行执行。
 * 适用于需要跨节点、跨调用保持一致性的场景（如库存扣减、订单状态机）。
 *
 * 锁键来源（按优先级）：
 * 1. 请求头 `X-Lock-Key`
 * 2. 请求参数 `_lockKey`
 * 3. 默认 `fluxion:lock:workflow:{workflowId}`
 *
 * 等待时间与租约可通过构造参数配置，默认不等待、租约 30 秒。
 * 如需更细粒度控制，建议使用函数级 [com.fluxion.decorator.impl.lock.DistributedLockDecorator]。
 */
class DistributedLockRouter(
    private val delegate: WorkflowRouter,
    private val lockProvider: DistributedLockProvider,
    private val waitMillis: Long = 0L,
    private val leaseMillis: Long = 30_000L,
    private val retry: Int = 0,
    private val retryIntervalMillis: Long = 100L,
    private val sync: Boolean = false
) : WorkflowRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 请求头中携带的锁键名称 */
        const val HEADER_LOCK_KEY = "X-Lock-Key"

        /** 请求参数中携带的锁键名称 */
        const val PARAM_LOCK_KEY = "_lockKey"

        /** 默认锁键前缀 */
        const val DEFAULT_PREFIX = "fluxion:lock:workflow:"
    }

    override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking {
        executeSuspend(request)
    }

    override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
        val lockKey = resolveLockKey(request)
        val lock = lockProvider.acquire(lockKey, leaseMillis, waitMillis, retry, retryIntervalMillis, sync)
            ?: throw LockAcquisitionException(
                "Failed to acquire distributed lock for workflow [${request.workflowId}] key=$lockKey"
            )

        log.debug("Acquired distributed lock for workflow [${request.workflowId}] key=$lockKey")
        try {
            return delegate.executeSuspend(request)
        } finally {
            try {
                lockProvider.release(lock)
                log.debug("Released distributed lock for workflow [${request.workflowId}] key=$lockKey")
            } catch (ex: Exception) {
                log.warn("Failed to release distributed lock for workflow [${request.workflowId}] key=$lockKey", ex)
            }
        }
    }

    private fun resolveLockKey(request: UnifiedRequest): String {
        val header = request.headers[HEADER_LOCK_KEY]
        if (!header.isNullOrBlank()) return header

        val param = request.params[PARAM_LOCK_KEY]?.toString()
        if (!param.isNullOrBlank()) return param

        val workflowId = request.workflowId ?: request.protocol
        return "$DEFAULT_PREFIX$workflowId"
    }
}
