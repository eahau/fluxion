package com.fluxion.core.engine

import com.fluxion.core.value.ExecutionMeta

/**
 * Dead-letter queue SPI — destination for workflow / node failures that
 * operators must inspect manually.
 *
 * Typical trigger points (see [DeadLetterEntry.FailureKind]):
 *  1. A node with `errorStrategy=RETRY` exhausts its retry budget.
 *  2. A Saga compensation function throws (the forward pass already failed).
 *  3. Workflow-level schema validation fails for the final output.
 *
 * Implementations:
 *  - LoggingDeadLetterQueue — logs entries only (dev default).
 *  - DbDeadLetterQueue — persists to `wf_dead_letter` for admin UI listing
 *    and manual replay.
 *  - MqDeadLetterQueue — forwards to a Kafka/RabbitMQ DLQ topic for stream
 *    processors or on-call integrations.
 *
 * A [NOOP] default is provided so engine callers can unconditionally
 * invoke `enqueue` without requiring a specific implementation.
 */
interface DeadLetterQueue {

    /**
     * Enqueue a failure entry for later operator action.
     *
     * Implementations MUST NOT throw — callers wrap enqueue in try/catch
     * but a well-behaved DLQ should internally swallow transport /
     * persistence errors.
     */
    fun enqueue(entry: DeadLetterEntry)

    companion object {
        /** No-op sentinel used when no real DLQ is configured. */
        val NOOP: DeadLetterQueue = object : DeadLetterQueue {
            override fun enqueue(entry: DeadLetterEntry) {}
        }
    }
}

/**
 * Immutable DLQ entry.
 *
 * Captures everything a human or automated replay tool needs to reproduce
 * the failure: execution identity, failing node, the error, and the full
 * inputs + [ExecutionMeta] snapshot.
 *
 * @param executionId unique workflow execution ID
 * @param workflowId workflow definition ID
 * @param workflowName workflow definition name (for operator readability)
 * @param nodeId failing node ID or `null` for workflow-level failures
 * @param nodeName failing node name or `null` for workflow-level failures
 * @param errorMessage exception message (may be null)
 * @param errorType simple exception class name (may be null)
 * @param failureKind category of the failure — see [FailureKind]
 * @param inputs workflow-level inputs at the time of failure
 * @param meta full [ExecutionMeta] snapshot
 * @param enqueuedAt epoch-millis timestamp (set automatically)
 */
data class DeadLetterEntry(
    val executionId: String,
    val workflowId: String,
    val workflowName: String,
    val nodeId: String?,
    val nodeName: String?,
    val errorMessage: String?,
    val errorType: String?,
    val failureKind: FailureKind,
    val inputs: Map<String, Any>,
    val meta: ExecutionMeta,
    val enqueuedAt: Long = System.currentTimeMillis()
) {
    /** Why was this entry placed onto the DLQ. */
    enum class FailureKind {
        /** Node-level error with `errorStrategy=FAIL` (or equivalent fallback failure). */
        NODE_FAILED,
        /** Retry budget exhausted by [WorkflowEngine.scheduleRetryAttempt]. */
        RETRY_EXHAUSTED,
        /** Saga compensation function threw during rollback. */
        COMPENSATION_FAILED,
        /** Workflow-level output schema or validation failure. */
        WORKFLOW_FAILED
    }
}
