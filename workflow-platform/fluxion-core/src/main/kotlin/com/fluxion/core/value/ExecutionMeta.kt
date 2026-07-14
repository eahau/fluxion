package com.fluxion.core.value

/**
 * Immutable metadata bag carried through every stage of a single execution.
 *
 * Stored once at workflow start (see [ImmutableExecutionState.start]) and
 * threaded through nodes, decorators and the execution log.  All fields are
 * trivially serializable (no framework types) so the object is safe to
 * attach to MDC, distribute to threads, or persist in audit logs.
 */
data class ExecutionMeta(
    /** Owning workflow id (matches [WorkflowDefinition.id]). */
    val workflowId: String,
    /** Human-readable workflow name; used for log messages and metrics tags. */
    val workflowName: String,
    /** Definition version; incremented by the admin console on publish. */
    val version: Int,
    /** Tenant / application group; drives Redis keys and multi-tenant routing. */
    val appGroup: String? = null,
    /** Unique per-execution UUID. */
    val executionId: String,
    /** Wall-clock start time (`System.currentTimeMillis()`). */
    val startTime: Long
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    /** Fluent builder for call-sites that construct metadata piecemeal. */
    class Builder {
        var workflowId: String = ""
        var workflowName: String = ""
        var version: Int = 0
        var appGroup: String? = null
        var executionId: String = ""
        var startTime: Long = 0L

        fun workflowId(v: String) = apply { workflowId = v }
        fun workflowName(v: String) = apply { workflowName = v }
        fun version(v: Int) = apply { version = v }
        fun appGroup(v: String?) = apply { appGroup = v }
        fun executionId(v: String) = apply { executionId = v }
        fun startTime(v: Long) = apply { startTime = v }

        fun build() = ExecutionMeta(
            workflowId = workflowId,
            workflowName = workflowName,
            version = version,
            appGroup = appGroup,
            executionId = executionId,
            startTime = startTime
        )
    }
}
