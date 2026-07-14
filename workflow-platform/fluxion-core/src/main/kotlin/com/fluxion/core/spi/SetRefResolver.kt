package com.fluxion.core.spi

/**
 * Runtime side-channel that maps a `functionRef` (string used by workflow nodes)
 * to a **target sub-workflow id** when the referenced function is really a
 * `SET_REF` function — i.e. a published reusable function-set (DAG-of-functions
 * exposed with a stable function alias).
 *
 * ### Why a side-channel instead of extending FunctionResolver?
 *
 * FunctionResolver returns `WorkflowFunction<Any>` — a lambda-style callable
 * unit — which is the correct abstraction for BUILTIN / SCRIPT / EXTERNAL
 * functions. A function-set reference, by contrast, is not a callable; it is an
 * instruction to "run the referenced DAG as a nested invocation", which is the
 * exact same concern [com.fluxion.core.engine.control.SubWorkflowExecutor]
 * already solves for `NodeType.SUB_WORKFLOW`.
 *
 * So instead of forcing FunctionResolver to return something-not-callable, we
 * give DagExecutor a tiny dedicated hook; on miss (`null`) execution falls back
 * to the standard FunctionResolver.resolve path, maintaining 100% backward
 * compatibility.
 *
 * Implementations are typically Spring-managed beans wired into the
 * DagExecutor constructor:
 *   - **Admin side**: `DbBackedSetRefResolver` — reads from `wf_function`
 *   - **Worker side**: **TODO** push SET_REF rows through the same push-model
 *     mechanism that propagates regular functions, or resolve via
 *     admin-HTTP call in a small `Caffeine` cache.
 *   - **Tests**: memory map (`InMemorySetRefResolver`)
 */
interface SetRefResolver {
    /**
     * Resolve a SET_REF alias to its concrete execution target, or `null` when
     * `functionRef` is not a published function-set (callers then fall back to
     * standard function lookup).
     *
     * Returned [SetRefTarget.workflowIdOverrides] map is merged into the node's
     * existing `params` so the set-definition's default `inputMapping` /
     * `maxDepth` can be overridden per-node (user node configs win over global
     * defaults from publish-time).
     */
    fun resolve(functionRef: String): SetRefTarget?
}

/**
 * Result of a successful SET_REF lookup. The DagExecutor treats this as an
 * implicit SUB_WORKFLOW node — see the matching dispatch code.
 *
 * @property targetWorkflowId   stable `WfDefinition.workflowId` to invoke
 * @property workflowParams     merged `SUB_WORKFLOW` params (e.g. publish-time
 *                               default `maxDepth`, global `inputMapping` hints);
 *                               values here have lower priority than whatever
 *                               the node declares in its own `params` map.
 */
data class SetRefTarget(
    val targetWorkflowId: String,
    val workflowParams: Map<String, Any> = emptyMap()
)
