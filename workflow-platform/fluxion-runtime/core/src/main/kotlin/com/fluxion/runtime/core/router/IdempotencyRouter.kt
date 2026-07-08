package com.fluxion.runtime.core.router

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.UnifiedResponse
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.core.engine.CachedExecution
import com.fluxion.core.engine.IdempotencyStore
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.slf4j.*
/**
 * Idempotency-aware [WorkflowRouter] decorator.
 *
 * Adds at-most-once semantics on top of any inner router by keying results
 * against a caller-supplied idempotency key. Supports three outcomes for
 * every request:
 * 1. **Cache HIT**  → return the saved [CachedExecution] directly, no DAG run.
 * 2. **Cache MISS** → run the inner delegate; save its result on success.
 * 3. **No key**     → transparently passthrough to the delegate.
 *
 * Threading / cache-stampede note: concurrent identical keys can still
 * double-execute (there is no built-in lock here). That's intentional:
 * workflows are expected to be eventually-consistent under re-execution.
 * For strict serialization, stack a [DistributedLockRouter] **outside** this
 * decorator (see [FluxionRuntimeAutoConfiguration] for the canonical order).
 *
 * @param delegate Inner router to wrap
 * @param store    Idempotency cache backend (Redis, DB, in-memory… up to impl)
 */
class IdempotencyRouter(
    private val delegate: WorkflowRouter,
    private val store: IdempotencyStore
) : WorkflowRouter {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Header name used by callers to supply an idempotency key. */
        const val HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key"
    }

    override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking { executeSuspend(request) }

    override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
        val key = request.headers[HEADER_IDEMPOTENCY_KEY]
        if (key.isNullOrBlank()) {
            return delegate.executeSuspend(request)
        }

        val workflowId = request.workflowId ?: request.protocol

        val cached = store.get(key, workflowId)
        if (cached != null) {
            log.debug { "Idempotency cache HIT key=$key workflow=$workflowId executionId=${cached.executionId}" }
            return cached.toResponse()
        }

        log.debug { "Idempotency cache MISS key=$key workflow=$workflowId, executing workflow" }
        val response = delegate.executeSuspend(request)

        if (response.success) {
            try {
                store.put(key, response.toEngineResult(), workflowId)
            } catch (ex: Exception) {
                log.warn(ex) { "Failed to cache idempotency result key=$key workflow=$workflowId" }
            }
        }

        return response
    }

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
