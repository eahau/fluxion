package com.fluxion.core.debug

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.core.value.RerunResult
import org.slf4j.*
import org.slf4j.info
import org.slf4j.warn
import org.slf4j.error
import org.slf4j.debug

/**
 * Developer-facing debugging and replay service on top of the core engine.
 *
 * Provides three orthogonal primitives built on a single immutable state +
 * trace model:
 *
 * 1. **Rerun from node** -- reload a persisted execution, truncate outputs
 *    after a chosen node, and replay the tail with optional overridden input.
 *    Used by SREs to recover from transient downstream failures without
 *    restarting the entire workflow.
 *
 * 2. **Single-step execution** -- execute exactly one node, return a new
 *    [DebugSnapshot]; used by the admin console debugger UI.
 *
 * 3. **Continue-to-breakpoint** -- step forward until either the next
 *    breakpoint node is reached or the workflow terminates.
 *
 * The service deliberately avoids storing session state itself: callers keep
 * [DebugSnapshot] (serializable, cheap) and pass it back in.
 */
class DebugService(
    private val engine: WorkflowEngine,
    private val executionLogRepository: ExecutionLogRepository,
    private val definitionLoader: DefinitionLoader
) {
    private val log = LoggerFactory.getLogger(DebugService::class.java)

    // ------------------------------------------------------------------
    // Rerun
    // ------------------------------------------------------------------

    /**
     * Resume a previously completed / failed execution starting from
     * `targetNodeId`.
     *
     * All node outputs *before* `targetNodeId` are carried over verbatim
     * from the archived state; anything at or after `targetNodeId` is
     * re-executed. If `overrideInput` is provided it is fed as the direct
     * input to `targetNodeId`; otherwise the original predecessor output
     * is reused.
     *
     * @param executionId    id of the original archived execution
     * @param targetNodeId   first node to re-execute
     * @param overrideInput  optional override for the target node's input
     * @param functionRegistry function resolver (defaults to the engine's own)
     * @return the replay result containing the final state + new trace
     */
    suspend fun rerunFromNode(
        executionId: String,
        targetNodeId: String,
        overrideInput: Any?,
        functionRegistry: FunctionResolver = engine.functionRegistry
    ): RerunResult {
        val originalResult = executionLogRepository.findByExecutionId(executionId)
            ?: throw IllegalArgumentException("Execution not found: `$executionId")

        val originalState = originalResult.finalState!!
        val def = loadDefinition(executionId)

        val targetIndex = findNodeIndex(def, targetNodeId)
        require(targetIndex >= 0) { "Node not found in workflow: $targetNodeId" }

        // Preserve all node outputs before the target node
        val priorOutputs = originalState.nodeOutputs.entries
            .filter { (key, _) -> isBeforeNode(def, key, targetNodeId) }
            .associate { it.key to it.value }

        val replayState = ImmutableExecutionState
            .start(def, originalState.inputs)
            .mergeNodeOutputs(priorOutputs)

        val directInput = overrideInput ?: getPrevNodeOutput(originalState, def, targetNodeId)

        val nodesToRerun = def.nodes.subList(targetIndex, def.nodes.size)

        log.info { "Rerunning from node [$targetNodeId] in execution [$executionId], ${nodesToRerun.size} nodes to execute" }

        return engine.executePartial(nodesToRerun, directInput, replayState, functionRegistry)
    }

    // ------------------------------------------------------------------
    // Step / Continue
    // ------------------------------------------------------------------

    /**
     * Execute exactly the next pending node recorded in `snap` and return
     * an updated snapshot (with a paused-at marker if the new *next* node
     * is a breakpoint).
     */
    suspend fun step(snap: DebugSnapshot, functionRegistry: FunctionResolver = engine.functionRegistry): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        return doStep(snap, def, functionRegistry)
    }

    /**
     * Step forward repeatedly until either the workflow ends or we land on
     * a node whose id is present in `snap.breakpoints`.
     */
    suspend fun continueToBreakpoint(snap: DebugSnapshot, functionRegistry: FunctionResolver = engine.functionRegistry): DebugSnapshot {
        val def = definitionLoader.load(snap.workflowId)
        var current = snap
        val nodes = def.nodes

        while (current.nextNodeIndex < nodes.size) {
            current = doStep(current, def, functionRegistry)
            if (current.pausedAtNodeId != null) break
        }
        return current
    }

    // ------------------------------------------------------------------
    // Snapshot construction
    // ------------------------------------------------------------------

    /**
     * Convert a complete [EngineResult] (e.g. from a fresh DAG run) into a
     * [DebugSnapshot] suitable for the step/continue primitives below.
     *
     * @param result           completed engine result
     * @param workflowId       owning workflow id
     * @param workflowVersion  workflow version -- must match subsequent loads
     * @param breakpoints      node ids that trigger a pause on entry
     * @param mockConfig       active MockConfig (injected into [DebugContext])
     */
    fun createSnapshot(
        result: EngineResult,
        workflowId: String,
        workflowVersion: Int,
        breakpoints: Set<String>?,
        mockConfig: MockConfig = MockConfig.EMPTY
    ): DebugSnapshot = DebugSnapshot(
        workflowId = workflowId,
        workflowVersion = workflowVersion,
        executionState = result.finalState!!,
        traces = result.trace,
        breakpoints = breakpoints ?: emptySet(),
        mockConfig = mockConfig,
        pausedAtNodeId = null,
        nextNodeIndex = result.trace.size
    )

    // ------------------------------------------------------------------
    // Private Helpers
    // ------------------------------------------------------------------

    /** Execute a single step: compute direct input, run the node, append the record, return new context. */
    private suspend fun doStep(snap: DebugSnapshot, def: WorkflowDefinition, functionRegistry: FunctionResolver): DebugSnapshot {
        val ctx = DebugSnapshot.restore(snap, def)
        val idx = ctx.nextNodeIndex
        val nodes = def.nodes

        if (idx >= nodes.size) return snap

        val node = nodes[idx]
        val state = ctx.executionState

        val directInput: Any? = if (idx == 0) state.inputs else state.getNodeOutput(nodes[idx - 1].id)

        val trace = ctx.traces.toMutableList()
        val record = engine.executeNode(node, directInput, state, functionRegistry)
        trace.add(record)

        val newState = state.withNodeOutput(node.id, record.output)
        val pausedAt = if (ctx.breakpoints.contains(node.id)) node.id else null

        return DebugContext(newState, ctx.mockConfig, ctx.breakpoints, trace, idx + 1)
            .toSnapshot(snap.workflowId, snap.workflowVersion, pausedAt)
    }

    private fun loadDefinition(executionId: String): WorkflowDefinition {
        val workflowId = executionLogRepository.findWorkflowIdByExecutionId(executionId)
            ?: throw IllegalArgumentException("No workflow bound to execution: `$executionId")
        return definitionLoader.load(workflowId)
    }

    private fun findNodeIndex(def: WorkflowDefinition, nodeId: String): Int =
        def.nodes.indexOfFirst { it.id == nodeId }

    private fun isBeforeNode(def: WorkflowDefinition, nodeAId: String, nodeBId: String): Boolean {
        val a = findNodeIndex(def, nodeAId)
        val b = findNodeIndex(def, nodeBId)
        return a >= 0 && b >= 0 && a < b
    }

    private fun getPrevNodeOutput(state: ImmutableExecutionState, def: WorkflowDefinition, targetNodeId: String): Any? {
        val idx = findNodeIndex(def, targetNodeId)
        if (idx <= 0) return state.inputs
        return state.getNodeOutput(def.nodes[idx - 1].id)
    }

    // ------------------------------------------------------------------
    // SPI Interfaces
    // ------------------------------------------------------------------

    /**
     * Repository SPI used by [rerunFromNode] to look up the archived
     * execution result and its owning workflow id. Implemented by the
     * admin console module (workflow-admin).
     */
    interface ExecutionLogRepository {
        fun findByExecutionId(executionId: String): EngineResult?
        fun findWorkflowIdByExecutionId(executionId: String): String?
    }

    /**
     * Workflow-definition loader SPI. Implemented by the admin console
     * module (workflow-admin) which reads definitions from its database.
     */
    interface DefinitionLoader {
        fun load(workflowId: String): WorkflowDefinition
    }
}
