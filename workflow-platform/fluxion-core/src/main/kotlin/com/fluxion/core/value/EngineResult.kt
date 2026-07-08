package com.fluxion.core.value

import com.fluxion.core.model.ImmutableExecutionState

/**
 * Result envelope returned to callers of the workflow engine.
 *
 * [WorkflowEngine] and [DagExecutor] always return an `EngineResult`; adapter
 * layers (HTTP, Dubbo, gRPC, ...) translate it into their native response
 * shape.  Both success and failure paths are represented uniformly so that
 * tracing metadata ([executionId], [trace], [finalState]) is available in
 * every outcome.
 */
data class EngineResult(
    /** True when the workflow completed without propagating an error. */
    val success: Boolean,
    /** Terminal output of the workflow (last node's output).  null on failure. */
    val data: Any? = null,
    /** Unique execution id for correlation / admin-console lookup. */
    val executionId: String? = null,
    /** Human-readable error description; non-null iff [success] == false. */
    val errorMsg: String? = null,
    /** Chronological trace of every node execution (populated in dev / audit mode). */
    val trace: MutableList<NodeExecutionRecord> = mutableListOf(),
    /** Snapshot of the final execution state for inspection / replay. */
    val finalState: ImmutableExecutionState? = null
) {
    companion object {
        /** Build a success result from the terminal execution state. */
        @JvmStatic
        fun success(data: Any?, state: ImmutableExecutionState) = EngineResult(
            success = true,
            data = data,
            executionId = state.meta.executionId,
            finalState = state
        )

        /** Build a failure result with an optional execution id (may be absent on early errors). */
        @JvmStatic
        fun failure(errorMsg: String?, executionId: String?) = EngineResult(
            success = false,
            errorMsg = errorMsg,
            executionId = executionId
        )

        /** Fluent builder used by execution paths that accumulate trace incrementally. */
        @JvmStatic
        fun builder() = Builder()
    }

    /** Fluent builder for [EngineResult]. */
    class Builder {
        var success: Boolean = false
        var data: Any? = null
        var executionId: String? = null
        var errorMsg: String? = null
        var trace: MutableList<NodeExecutionRecord> = mutableListOf()
        var finalState: ImmutableExecutionState? = null

        fun success(v: Boolean) = apply { success = v }
        fun data(v: Any?) = apply { data = v }
        fun executionId(v: String?) = apply { executionId = v }
        fun errorMsg(v: String?) = apply { errorMsg = v }
        fun trace(v: MutableList<NodeExecutionRecord>) = apply { trace = v }
        fun finalState(v: ImmutableExecutionState?) = apply { finalState = v }

        fun build() = EngineResult(
            success = success,
            data = data,
            executionId = executionId,
            errorMsg = errorMsg,
            trace = trace,
            finalState = finalState
        )
    }
}
