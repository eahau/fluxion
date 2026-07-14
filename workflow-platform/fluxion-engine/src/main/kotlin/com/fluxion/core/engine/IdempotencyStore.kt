package com.fluxion.core.engine

import com.fluxion.core.value.EngineResult

/**
 * Idempotency store SPI -- caches execution results by caller-supplied key.
 *
 * Intended to guard HTTP adapters / API gateways that receive an
 * `X-Idempotency-Key` header: when the same key is replayed within the TTL
 * window the cached [CachedExecution] is returned instead of re-running the
 * workflow.
 *
 * Typical implementations:
 *  - InMemoryIdempotencyStore — dev / single-node deployments
 *  - RedisIdempotencyStore — horizontally scalable with per-key TTL (24h default)
 *  - DbIdempotencyStore — relational-backed persistence for auditability
 *
 * A [NOOP] default is provided so the adapter layer can always call the
 * store without requiring an implementation to be explicitly wired.
 */
interface IdempotencyStore {

    /**
     * Fetch a previously cached execution for `key`.
     *
     * @param key unique idempotency key (usually a UUID from the caller)
     * @param workflowId optional workflow scope (may be used by implementations
     *   to partition caches; `null` means global/shared cache)
     * @return the cached execution, or `null` when no match is found
     */
    fun get(key: String, workflowId: String? = null): CachedExecution?

    /**
     * Cache a successful or failed execution for future idempotent replays.
     *
     * @param key unique idempotency key
     * @param result execution result to persist
     * @param workflowId owning workflow ID (for partitioning)
     */
    fun put(key: String, result: EngineResult, workflowId: String)

    companion object {
        /** No-op sentinel used when no real store is configured. */
        val NOOP: IdempotencyStore = object : IdempotencyStore {
            override fun get(key: String, workflowId: String?): CachedExecution? = null
            override fun put(key: String, result: EngineResult, workflowId: String) {}
        }
    }
}

/**
 * Cached idempotent execution record.
 *
 * Captures the minimum set of fields required to faithfully replay a prior
 * execution result through the adapter layer without re-running nodes.
 *
 * @param success whether the original execution succeeded
 * @param data output data produced by the original execution
 * @param executionId original execution ID (so callers can correlate logs)
 * @param errorMsg error message when [success] is false
 * @param workflowId owning workflow ID
 * @param cachedAt epoch-millis timestamp for TTL bookkeeping
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
        /** Build a [CachedExecution] directly from a live [EngineResult]. */
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
