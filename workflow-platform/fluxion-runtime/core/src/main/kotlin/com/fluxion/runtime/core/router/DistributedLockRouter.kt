package com.fluxion.runtime.core.router

import com.fluxion.inbound.spi.UnifiedRequest
import com.fluxion.inbound.spi.UnifiedResponse
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.core.exception.LockAcquisitionException
import com.fluxion.core.lock.DistributedLockProvider
import kotlinx.coroutines.runBlocking
import org.slf4j.*
/**
 * Distributed-lock guarding [InboundRouter] decorator.
 *
 * Serializes concurrent requests that share the same lock key, guaranteeing
 * at most one in-flight workflow execution per key across the entire cluster.
 * Required for scenarios like inventory decrement or order state machines
 * where interleaved DAGs would break correctness invariants.
 *
 * Lock key resolution order (first match wins):
 * 1. HTTP header `X-Lock-Key`
 * 2. Request param `_lockKey`
 * 3. Fallback: `fluxion:lock:workflow:{workflowId}`
 *
 * Acquire/release timings are configurable via constructor — defaults are
 * chosen for short-lived synchronous workflows (30s lease, zero wait).
 * If the lock cannot be obtained in time, [LockAcquisitionException] is
 * thrown so adapters can translate it to the appropriate HTTP/gRPC status.
 *
 * **Decorator ordering note**: stack this OUTSIDE [IdempotencyRouter] so a
 * MISS on idempotency-cache still gets serialized before hitting the DAG.
 *
 * @param delegate             Inner router to wrap
 * @param lockProvider         Cluster-wide lock backend (Redis, ZK, etc.)
 * @param waitMillis           Max millis to block waiting for lock acquisition
 * @param leaseMillis          Automatic lease expiry millis on the lock
 * @param retry                Number of re-acquire attempts on transient failure
 * @param retryIntervalMillis  Pause between retries
 * @param sync                 If true, use sync (fair) acquire variant where supported
 */
class DistributedLockRouter(
    private val delegate: InboundRouter,
    private val lockProvider: DistributedLockProvider,
    private val waitMillis: Long = 0L,
    private val leaseMillis: Long = 30_000L,
    private val retry: Int = 0,
    private val retryIntervalMillis: Long = 100L,
    private val sync: Boolean = false
) : InboundRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** HTTP header that supplies an explicit lock key. */
        const val HEADER_LOCK_KEY = "X-Lock-Key"

        /** Request-params key that supplies an explicit lock key. */
        const val PARAM_LOCK_KEY = "_lockKey"

        /** Prefix for the per-workflow default lock key. */
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

        log.debug { "Acquired distributed lock for workflow [${request.workflowId}] key=$lockKey" }
        try {
            return delegate.executeSuspend(request)
        } finally {
            try {
                lockProvider.release(lock)
                log.debug { "Released distributed lock for workflow [${request.workflowId}] key=$lockKey" }
            } catch (ex: Exception) {
                log.warn(ex) { "Failed to release distributed lock for workflow [${request.workflowId}] key=$lockKey" }
            }
        }
    }

    /**
     * Determine the lock key for a request by checking, in priority order:
     * explicit header → explicit param → per-workflow default.
     *
     * @param request Incoming unified request
     * @return Resolved lock key (never blank)
     */
    private fun resolveLockKey(request: UnifiedRequest): String {
        val header = request.headers[HEADER_LOCK_KEY]
        if (!header.isNullOrBlank()) return header

        val param = request.params[PARAM_LOCK_KEY]?.toString()
        if (!param.isNullOrBlank()) return param

        val workflowId = request.workflowId ?: request.protocol
        return "$DEFAULT_PREFIX$workflowId"
    }
}
