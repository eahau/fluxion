package com.fluxion.core.engine

import com.fluxion.core.engine.control.LoopExecutor
import com.fluxion.core.engine.control.SubWorkflowExecutor
import com.fluxion.core.engine.control.WaitExecutor
import com.fluxion.core.enums.NodeType
import com.fluxion.core.exception.CyclicDependencyException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProvider
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.model.SchemaFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import org.slf4j.*

/**
 * DAG orchestrator that drives parallel node execution via Kotlin Coroutines.
 *
 * Implements Kahn's topological-scheduling algorithm so nodes with zero
 * unmet dependencies can run concurrently on the injected dispatcher:
 *  1. Validate every node and the workflow input contract.
 *  2. Compute per-node in-degree from [WorkflowNode.dependsOn] and build a
 *     `parentId -> children` successor map (O(n)).
 *  3. Seed a ready queue with nodes whose in-degree is 0.
 *  4. Launch all ready nodes in parallel; synchronously wait for the first
 *     to complete, then decrement successor in-degrees and loop.
 *  5. Merge the final trace (sync + async nodes) and return an
 *     [EngineResult] carrying the last topological node's output.
 *
 * Nodes with `asyncExecution=true` are fire-and-forget via `launch`; they
 * do not block downstream successors and their records are appended to the
 * trace out of order (captured via a thread-safe concurrent queue).
 *
 * [WorkflowEngine] is used as the per-node execution primitive so
 * decorators, schema validation, retries and error strategies are all
 * inherited without duplication.
 */
class DagExecutor(
    private val engine: WorkflowEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val taskInterceptor: TaskInterceptor = TaskInterceptor.NOOP,
    private val executionTracker: ExecutionTracker? = null,
    private val loopExecutor: LoopExecutor? = null,
    private val waitExecutor: WaitExecutor? = null,
    private val subWorkflowExecutor: SubWorkflowExecutor? = null,
    private val workflowDecoratorRegistry: com.fluxion.decorator.decorator.WorkflowDecoratorRegistry? = null,
    private val providerRegistry: SchemaDataProviderRegistry? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Convenience access to the engine's resolver (used for mocks in tests). */
    val functionRegistry: FunctionResolver get() = engine.functionRegistry

    /**
     * Execute a DAG workflow.
     *
     * Applies the optional chain of workflow-level decorators before the
     * real [executeDecorated] so that e.g. tracing / rate-limit / metrics
     * decorators can wrap the entire DAG run with a single interception.
     */
    suspend fun execute(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        functionRegistry: FunctionResolver = engine.functionRegistry
    ): EngineResult {
        val doExecute: suspend () -> EngineResult = { executeDecorated(def, rawInput, functionRegistry) }
        val decorated = def.workflowDecorators.orEmpty()
            .foldRight(doExecute) { decoratorName, next ->
                val decorator = workflowDecoratorRegistry?.resolve(decoratorName)
                    ?: throw IllegalStateException(
                        "Workflow decorator '$decoratorName' not found for workflow [${def.id}]. " +
                            "Ensure WorkflowDecoratorRegistry is configured."
                    )
                suspend { decorator.decorate(def, rawInput, next) }
            }
        return decorated()
    }

    /**
     * Core DAG execution implementation.
     *
     * See class-level KDoc for the Kahn's algorithm walkthrough. This
     * method wraps its body in a [coroutineScope] so every child async job
     * is structurally supervised (a crash cancels siblings).
     */
    private suspend fun executeDecorated(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        functionRegistry: FunctionResolver = engine.functionRegistry
    ): EngineResult = coroutineScope {

        val wrappedDispatcher = taskInterceptor.wrap(dispatcher)
        val lockedNodes = def.nodes.toList()

        lockedNodes.forEach { it.validate() }

        engine.validateWorkflowInput(def, rawInput)

        val provider = resolveProvider(def.inputSchemaFormat)
        @Suppress("UNCHECKED_CAST")
        val schemaAwareInput: Map<String, Any> =
            if (provider != null) SchemaBackedMap.wrap(rawInput, provider) as Map<String, Any>
            else rawInput

        val sortedIds = def.sortedNodeIds
        if (sortedIds.isEmpty() && lockedNodes.isNotEmpty()) {
            throw CyclicDependencyException(def.id)
        }

        var state = ImmutableExecutionState.start(def, schemaAwareInput)

        executionTracker?.trackStart(
            executionId = state.meta.executionId,
            workflowId = def.id,
            workflowName = def.name,
            version = def.version,
            inputs = rawInput,
            nodeIds = lockedNodes.map { it.id }
        )

        try {

            val inDegree = lockedNodes.associate { node ->
                node.id to (node.dependsOn?.size ?: 0)
            }.toMutableMap()

            // Build successor map once — O(n) vs repeatedly filtering per completion.
            val successorMap: Map<String, List<WorkflowNode>> = buildSuccessorMap(lockedNodes)
            val readyQueue = ArrayDeque(lockedNodes.filter { (inDegree[it.id] ?: 0) == 0 })
            val pendingResults = mutableMapOf<String, Deferred<NodeExecutionRecord>>()
            val executionTrace  = mutableListOf<NodeExecutionRecord>()

            // Async trace records are added from launch() workers concurrently — use a JUC collection.
            val asyncTraceRecords = java.util.concurrent.ConcurrentLinkedQueue<NodeExecutionRecord>()

            while (readyQueue.isNotEmpty() || pendingResults.isNotEmpty()) {
                while (readyQueue.isNotEmpty()) {
                    val node        = readyQueue.removeFirst()
                    val directInput = resolveDirectInput(node, state, provider)
                    val capturedState = state
                    if (node.asyncExecution) {
                        // Fire-and-forget async node; release successors immediately.
                        launch(wrappedDispatcher) {
                            val startNano = System.nanoTime()
                            try {
                                val record = engine.executeNode(node, directInput, capturedState, functionRegistry, def.inputSchemaFormat)
                                asyncTraceRecords.add(record)
                                log.debug { "Async DAG node [${record.nodeName}] completed, status=${record.status}, duration=${record.durationMs}ms" }
                            } catch (ex: Exception) {
                                val durationMs = (System.nanoTime() - startNano) / 1_000_000L
                                val failedRecord = NodeExecutionRecord.failed(node, ex, durationMs)
                                asyncTraceRecords.add(failedRecord)
                                log.error(ex) { "Async node [${node.name}] failed (non-blocking): ${ex.message}" }
                            }
                        }
                        releaseSuccessors(node.id, successorMap, inDegree, readyQueue)
                    } else {
                        pendingResults[node.id] = async(wrappedDispatcher) {
                            executeNodeDispatch(node, directInput, capturedState, functionRegistry, lockedNodes, def.inputSchemaFormat)
                        }
                    }
                }

                if (pendingResults.isEmpty()) continue

                val (completedId, record) = awaitFirst(pendingResults)
                pendingResults.remove(completedId)
                executionTrace.add(record)

                log.debug { "DAG node [${record.nodeName}] completed, status=${record.status}, duration=${record.durationMs}ms" }

                if (record.output != null) {
                    state = state.withNodeOutput(completedId, record.output)
                }

                releaseSuccessors(completedId, successorMap, inDegree, readyQueue)
            }

            executionTrace.addAll(asyncTraceRecords)

            val finalOutput = if (sortedIds.isNotEmpty()) state.getNodeOutput<Any?>(sortedIds.last()) else null
            engine.validateWorkflowOutput(def, finalOutput)
            EngineResult.success(finalOutput, state).also { it.trace.addAll(executionTrace) }

        } finally {
            executionTracker?.trackEnd(state.meta.executionId)
        }
    }

    // ---- Helpers ---------------------------------------------------------

    /**
     * Resolve the [SchemaDataProvider] for a schema format code.
     *
     * Swallows `IllegalArgumentException` (unknown format) and logs at
     * DEBUG so plain-Map workflows continue to work without a registered
     * provider for the default JSON-schema format.
     */
    private fun resolveProvider(schemaFormat: String?): SchemaDataProvider? {
        val registry = providerRegistry ?: return null
        val format = SchemaFormat.fromCodeOrNull(schemaFormat) ?: return null
        return try {
            registry.getProvider(format)
        } catch (e: IllegalArgumentException) {
            log.debug { "No SchemaDataProvider for format [$schemaFormat], falling back to Map access" }
            null
        }
    }

    /**
     * Dispatch a node execution to the correct type-specific executor.
     *
     *  - LOOP -> [LoopExecutor]
     *  - WAIT -> [WaitExecutor]
     *  - SUB_WORKFLOW -> [SubWorkflowExecutor]
     *  - everything else -> generic [WorkflowEngine.executeNode]
     */
    private suspend fun executeNodeDispatch(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        functionRegistry: FunctionResolver,
        allNodes: List<WorkflowNode>,
        schemaFormat: String? = null
    ): NodeExecutionRecord {
        return when (node.type) {
            NodeType.LOOP -> {
                val executor = loopExecutor
                    ?: throw WorkflowNodeException.timeout("LoopExecutor not configured; cannot execute LOOP node [${node.name}]", null)
                executor.execute(node, state, functionRegistry, allNodes)
            }
            NodeType.WAIT -> {
                val executor = waitExecutor
                    ?: throw WorkflowNodeException.timeout("WaitExecutor not configured; cannot execute WAIT node [${node.name}]", null)
                executor.execute(node, state)
            }
            NodeType.SUB_WORKFLOW -> {
                val executor = subWorkflowExecutor
                    ?: throw WorkflowNodeException.timeout("SubWorkflowExecutor not configured; cannot execute SUB_WORKFLOW node [${node.name}]", null)
                executor.execute(node, directInput, state, functionRegistry)
            }
            else -> engine.executeNode(node, directInput, state, functionRegistry, schemaFormat)
        }
    }

    /**
     * Wait for the first pending deferred to complete via `select`.
     *
     * Pairing each deferred's `onAwait` clause avoids the need to
     * cancel/re-join remaining deferreds and matches Kahn's semantics
     * where any single completion can unblock further nodes.
     */
    private suspend fun awaitFirst(
        pending: Map<String, Deferred<NodeExecutionRecord>>
    ): Pair<String, NodeExecutionRecord> = select {
        pending.forEach { (id, deferred) ->
            deferred.onAwait { record -> id to record }
        }
    }

    /**
     * Compute the `directInput` value forwarded into a node execution.
     *
     * Rules (in order):
     *  - Nodes with `dependsOn` never receive a `directInput` (they read
     *    everything via [NodeInput.declaredDeps]).
     *  - The most recent node output, if any, becomes the direct input.
     *  - Otherwise fall back to the workflow-level `state.inputs` (which is
     *    already wrapped as SchemaBackedMap when a provider exists).
     */
    private fun resolveDirectInput(
        node: WorkflowNode,
        state: ImmutableExecutionState,
        provider: SchemaDataProvider?
    ): Any? {
        if (!node.dependsOn.isNullOrEmpty()) return null
        val raw = state.nodeOutputs.values.lastOrNull() ?: return state.inputs
        return SchemaBackedMap.wrap(raw, provider)
    }

    /**
     * Build `parentId -> listOf(childNodes)` successor map.
     *
     * Pre-computed once so [releaseSuccessors] can operate in O(1) per
     * completed node instead of re-scanning all nodes to find parents.
     */
    private fun buildSuccessorMap(nodes: List<WorkflowNode>): Map<String, List<WorkflowNode>> {
        val map = mutableMapOf<String, MutableList<WorkflowNode>>()
        for (node in nodes) {
            node.dependsOn?.forEach { parentId ->
                map.getOrPut(parentId) { mutableListOf() }.add(node)
            }
        }
        return map
    }

    /**
     * Decrement in-degree for every successor of the just-completed node;
     * enqueue any successor that now has in-degree 0 into `readyQueue`.
     */
    private fun releaseSuccessors(
        completedId: String,
        successorMap: Map<String, List<WorkflowNode>>,
        inDegree: MutableMap<String, Int>,
        readyQueue: ArrayDeque<WorkflowNode>
    ) {
        successorMap[completedId]?.forEach { successor ->
            val newDegree = (inDegree[successor.id] ?: 1) - 1
            inDegree[successor.id] = newDegree
            if (newDegree == 0) readyQueue.add(successor)
        }
    }
}
