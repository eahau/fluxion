package com.fluxion.core.value

import com.fluxion.core.enums.NodeStatus
import com.fluxion.core.model.WorkflowNode

/**
 * Persistent/audit record of a single node execution.
 *
 * A new record is produced every time a node is attempted (including retries).
 * Records are appended to [EngineResult.trace] and optionally persisted by
 * [ExecutionLogStore.save].  All fields are nullable where appropriate so that
 * partial records can be created early in the execution lifecycle and then
 * enriched.
 */
data class NodeExecutionRecord(
    /** Node id (matches [WorkflowNode.id]). */
    val nodeId: String,
    /** Human-readable node name (matches [WorkflowNode.name]). */
    val nodeName: String,
    /** Final status of this particular attempt. */
    val status: NodeStatus,
    /** Value produced; may be null even on SUCCESS when the function has no output. */
    val output: Any?,
    /** Snapshot of the node's input; null unless explicitly captured for audit. */
    val input: Any?,
    /** Side effects announced by the function; used for Saga compensation. */
    val sideEffects: List<SideEffect>,
    /** Exception captured on failure; null on successful paths. */
    val error: Throwable?,
    /** Wall-clock duration of this attempt in milliseconds. */
    val durationMs: Long,
    /** 0-based attempt index (0 = first try, 1 = first retry, ...). */
    val retryAttempt: Int
) {
    /** Convenience getter for error message (null-safe). */
    val errorMessage: String? get() = error?.message

    companion object {
        /** Build a SUCCESS record without input capture. */
        @JvmStatic
        fun success(node: WorkflowNode, output: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SUCCESS,
            output = output, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** Build a SUCCESS record with the input captured (for replay / audit). */
        @JvmStatic
        fun successWithInput(node: WorkflowNode, output: Any?, input: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SUCCESS,
            output = output, input = input, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** Build a FAILED record capturing the underlying cause. */
        @JvmStatic
        fun failed(node: WorkflowNode, error: Throwable?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.FAILED,
            output = null, input = null, sideEffects = emptyList(),
            error = error, durationMs = durationMs, retryAttempt = 0
        )

        /** Build a SKIPPED record (error strategy SKIP). */
        @JvmStatic
        fun skipped(node: WorkflowNode, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.SKIPPED,
            output = null, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )

        /** Build a FALLBACK record (fallback function succeeded). */
        @JvmStatic
        fun fallback(node: WorkflowNode, output: Any?, durationMs: Long) = NodeExecutionRecord(
            nodeId = node.id, nodeName = node.name, status = NodeStatus.FALLBACK,
            output = output, input = null, sideEffects = emptyList(),
            error = null, durationMs = durationMs, retryAttempt = 0
        )
    }
}
