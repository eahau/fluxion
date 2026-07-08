package com.fluxion.core.model

import com.fluxion.core.value.ExecutionMeta
import java.util.UUID

/**
 * Snapshot of the execution state at a single point in the workflow's
 * lifecycle — immutable, new copies are produced as nodes complete.
 *
 * Thread-safe and trivially serializable; safe to hand to persistence,
 * Saga compensation planning, or admin-console query APIs.  The
 * companion factory [start] produces the initial empty state after
 * copying `workflowId` / `appGroup` onto every node so that nodes
 * don't need to keep a back-reference to their owning definition.
 */
class ImmutableExecutionState private constructor(
    /** Top-level inputs supplied by the workflow caller. */
    val inputs: Map<String, Any>,
    /** Append-only map of nodeId → output produced by that node. */
    val nodeOutputs: Map<String, Any?>,
    /** Execution metadata (ids, version, start time, ...). */
    val meta: ExecutionMeta
) {
    companion object {
        /**
         * Create the initial state for a fresh workflow execution.
         *
         * As a side-effect every node in [def] has its `workflowId` and
         * (blank) `appGroup` populated from the definition so that
         * downstream decorators (locks, rate-limits) can compute stable
         * Redis keys without carrying the whole definition pointer.
         */
        @JvmStatic
        fun start(def: WorkflowDefinition, rawInput: Map<String, Any>?): ImmutableExecutionState {
            def.nodes.forEach { node ->
                node.workflowId = def.id
                if (node.appGroup.isNullOrBlank()) {
                    node.appGroup = def.appGroup
                }
            }
            val meta = ExecutionMeta(
                workflowId = def.id,
                workflowName = def.name,
                version = def.version,
                appGroup = def.appGroup,
                executionId = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis()
            )
            return ImmutableExecutionState(
                inputs = rawInput?.toMap() ?: emptyMap(),
                nodeOutputs = emptyMap(),
                meta = meta
            )
        }
    }

    /** Return a new state that additionally records `nodeId → output`. */
    fun withNodeOutput(nodeId: String, output: Any?): ImmutableExecutionState =
        ImmutableExecutionState(inputs, nodeOutputs + (nodeId to output), meta)

    /** Merge a batch of parallel outputs (used after a DAG fan-in completes). */
    fun mergeNodeOutputs(parallelOutputs: Map<String, Any?>): ImmutableExecutionState =
        ImmutableExecutionState(inputs, nodeOutputs + parallelOutputs, meta)

    /** Typed input lookup — returns null on missing key or wrong type. */
    inline fun <reified T> getInput(key: String): T? = inputs[key] as? T

    /** Typed node-output lookup — returns null on missing key or wrong type. */
    inline fun <reified T> getNodeOutput(nodeId: String): T? = nodeOutputs[nodeId] as? T

    /** True iff the node has already produced a value (including explicit `null`). */
    fun hasNodeOutput(nodeId: String): Boolean = nodeOutputs.containsKey(nodeId)
}
