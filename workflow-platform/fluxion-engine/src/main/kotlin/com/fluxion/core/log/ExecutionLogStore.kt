package com.fluxion.core.log

import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.NodeExecutionRecord

/**
 * Persistence SPI for the per-node audit trace.
 *
 * The engine calls [save] for every completed (or failed) node *after* the
 * execution has been processed by error handlers.  Implementations decide
 * what to persist, how to batch writes, and which fields to index.
 *
 * Two reference implementations exist:
 *  - A no-op / logging-only default is auto-configured in
 *    `FluxionCoreAutoConfiguration` when no store bean is present.
 *  - `DbExecutionLogStore` (in the admin/runtime modules) writes to the
 *    `wf_execution_log` table for full lifecycle replay.
 */
interface ExecutionLogStore {

    /**
     * Whether saves should be attempted at all.
     *
     * Returning `false` lets the engine skip the overhead of materialising
     * records in hot paths (when disabled globally, per-tenant, etc.).
     */
    fun enabled(): Boolean

    /** Persist (or batch) a single node record alongside its execution metadata. */
    fun save(record: NodeExecutionRecord, meta: ExecutionMeta)

    /**
     * Load the chronological trace for a past execution (admin console /
     * replay API).  Default returns empty list because the logging-only
     * default store has no query capability.
     */
    fun loadTrace(executionId: String): List<NodeExecutionRecord> = emptyList()
}
