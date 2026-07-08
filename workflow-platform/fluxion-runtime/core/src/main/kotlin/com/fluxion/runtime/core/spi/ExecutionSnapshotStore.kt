package com.fluxion.runtime.core.spi

import com.fluxion.core.value.EngineResult

/**
 * Persistence SPI for workflow execution snapshots.
 *
 * The Runtime layer intentionally carries no database dependency so Workers
 * can remain stateless sidecars. Implementations supplied by downstream
 * integrations range from trivial to durable:
 * - No-op (null bean): snapshotting disabled entirely.
 * - Log-only default: [LoggingExecutionSnapshotStore] used when nothing else wired.
 * - Durable: write to Admin DB, Redis, S3, etc. for audit/debug replay.
 *
 * Implementations MUST tolerate transient write failures — the caller
 * ([RuntimeWorkflowRouter]) swallows any exception to keep the DAG result
 * path unaffected.
 */
interface ExecutionSnapshotStore {

    /**
     * Persist a single workflow execution outcome.
     *
     * @param result     Engine execution result (success flag, data, error, executionId)
     * @param workflowId Owning workflow identifier
     * @param version    Workflow definition version that produced the run
     */
    fun save(result: EngineResult, workflowId: String, version: Int)
}
