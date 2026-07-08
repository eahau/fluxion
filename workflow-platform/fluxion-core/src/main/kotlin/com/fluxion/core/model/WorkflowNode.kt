package com.fluxion.core.model

import com.fluxion.core.enums.ErrorStrategy
import com.fluxion.core.enums.NodeType
import com.fluxion.core.util.uncheckedCastGeneric

/**
 * A single node inside a [WorkflowDefinition] DAG.
 *
 * Combines execution concerns (what function to call, how to react to
 * failures, retries, timeouts) with graph concerns (depends-on, next /
 * conditional-next edges) and cross-cutting concerns (decorators with
 * per-node parameters, Saga compensation reference).
 */
data class WorkflowNode(
    /** Stable node id — unique within a single workflow definition. */
    var id: String = "",
    /** Human-readable name, surfaced in UI and logs/metrics tags. */
    var name: String = "",
    /** Owning workflow id; populated from WorkflowDefinition on load. */
    var workflowId: String = "",
    /** App / tenant group; inherited from the definition if blank. */
    var appGroup: String? = null,
    /** Function reference — e.g. `"builtin:httpCall"` or `"wf:errorWrapper"`. */
    var functionRef: String = "",
    /** Node classification used by WorkflowEngine to dispatch execution. */
    var type: NodeType = NodeType.CUSTOM,
    /** Static parameters merged into the [NodeInput.nodeParams] map at runtime. */
    var params: Map<String, Any>? = null,
    /** How the engine reacts when this node throws. */
    var errorStrategy: ErrorStrategy = ErrorStrategy.FAIL,
    /** Per-node execution timeout in milliseconds; `0` disables the timeout. */
    var timeoutMs: Int = 0,
    /** Maximum number of retries (ErrorStrategy.RETRY); bounded by [MAX_RETRY_COUNT]. */
    var retryCount: Int = 0,
    /** Exponential-backoff base in milliseconds; `0` falls back to RetryScheduler default. */
    var retryBaseMs: Int = 0,
    /** Function used for Saga compensation if this node produced [SideEffect]s. */
    var compensateFunctionRef: String? = null,
    /** Unconditional linear-next id. Used in non-DAG (linear) workflow graphs. */
    var next: String? = null,
    /** Conditional successor list — evaluated in order; first match wins. */
    var conditionalNexts: List<ConditionalNext>? = null,
    /** DAG predecessor ids — used by [DagTopology] to compute the execution order. */
    var dependsOn: List<String>? = null,
    /** Decorator refs applied to this node, e.g. `"logging:default"`, `"ratelimit:slidingWindow"`. */
    var decorators: List<String>? = null,
    /** Per-decorator parameter map: `decoratorName → { key → value }`. */
    var decoratorParams: Map<String, Map<String, Any>>? = null,
    /** Free-form human-readable note, surfaced in the admin console only. */
    var remark: String? = null,
    /**
     * If true the engine launches the node asynchronously (fire-and-forget
     * from the DAG perspective). Used for side branches that should not
     * hold up completion of the main graph.
     */
    var asyncExecution: Boolean = false
) {
    companion object {
        /** Upper bound on retry count to prevent accidentally explosive configs. */
        const val MAX_RETRY_COUNT = 5
    }

    /**
     * Extract a typed decorator parameter with a safe default.
     *
     * The cast is intentionally unchecked because decorator configs
     * originate as loosely-typed JSON maps from the admin console;
     * mismatches surface as ClassCastException at the call-site which
     * is exactly where the operator expects to see them.
     */
    fun <T> getDecoratorParam(decoratorName: String, paramKey: String, defaultValue: T): T {
        val p = decoratorParams?.get(decoratorName) ?: return defaultValue
        val v = p[paramKey] ?: return defaultValue
        return v.uncheckedCastGeneric()!!
    }

    /**
     * Lightweight structural validation invoked when a definition is
     * published. Currently guards [retryCount] against values greater
     * than [MAX_RETRY_COUNT] so that bad user config fails fast.
     */
    fun validate() {
        if (retryCount > MAX_RETRY_COUNT) {
            throw IllegalArgumentException(
                "Node [$id] retryCount=$retryCount exceeds maximum allowed ($MAX_RETRY_COUNT)"
            )
        }
    }
}
