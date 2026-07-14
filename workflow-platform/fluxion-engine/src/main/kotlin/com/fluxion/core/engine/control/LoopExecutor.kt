package com.fluxion.core.engine.control

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.value.NodeExecutionRecord
import org.slf4j.*

/**
 * Executor for LOOP-type workflow nodes.
 *
 * Iterates a configurable collection once per element, executing the
 * `loopBody` sub-graph (referenced node IDs) with `__loopItem` and
 * `__loopIndex` injected into each body node's params. The final node
 * output is the ordered list of each iteration's last body-node output.
 *
 * Node params (from [WorkflowNode.params]):
 *  - `collection`: literal `List`, a `{$ref: "nodeId"}` pointer to an
 *    upstream output, or any scalar (wrapped to a single-element list)
 *  - `iteratorVar`: **reserved for future use**; the item is currently
 *    always exposed via `__loopItem`
 *  - `maxIterations`: safety cap; larger collections are truncated and a
 *    warning is logged (default 1000)
 *  - `loopBody`: list of node IDs (from the owning workflow) executed
 *    sequentially per iteration
 *
 * Example JSON node:
 * ```json
 * {
 *   "id": "loop1",
 *   "type": "LOOP",
 *   "params": {
 *     "collection": [1, 2, 3],
 *     "maxIterations": 100
 *   },
 *   "loopBody": ["node_a", "node_b"]
 * }
 * ```
 */
class LoopExecutor(
    private val engine: WorkflowEngine
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DEFAULT_MAX_ITERATIONS = 1000
    }

    /**
     * Execute a LOOP node.
     *
     * Resolves the collection, runs the loop-body nodes sequentially for
     * each element (injecting `__loopItem` / `__loopIndex` into body-node
     * params) and returns the ordered list of iteration results.
     *
     * @param node LOOP node definition
     * @param state current workflow execution state
     * @param functionRegistry function resolver (also accepts a mock for tests)
     * @param allNodes every node in the owning workflow (used to resolve loopBody IDs)
     * @return success record whose `output` is the list of per-iteration results
     */
    suspend fun execute(
        node: WorkflowNode,
        state: ImmutableExecutionState,
        functionRegistry: FunctionResolver,
        allNodes: List<WorkflowNode>
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        val params = node.params ?: emptyMap()

        val collection = resolveCollection(params, state)
        val maxIterations = (params["maxIterations"] as? Number)?.toInt() ?: DEFAULT_MAX_ITERATIONS

        if (collection.isEmpty()) {
            log.debug { "Loop node [${node.name}]: empty collection, skipping" }
            return NodeExecutionRecord.success(node, emptyList<Any?>(), elapsedMs(startNano))
        }

        if (collection.size > maxIterations) {
            log.warn { "Loop node [${node.name}]: collection size ${collection.size} exceeds maxIterations=$maxIterations, truncating" }
        }

        val loopBodyIds = (params["loopBody"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val nodeIndex = allNodes.associateBy { it.id }
        val loopBodyNodes = loopBodyIds.mapNotNull { nodeIndex[it] }

        val results = mutableListOf<Any?>()
        var currentState = state
        val iterations = minOf(collection.size, maxIterations)

        for ((index, item) in collection.withIndex().take(iterations)) {
            log.debug { "Loop node [${node.name}]: iteration ${index + 1}/$iterations" }

            var iterState = currentState
            for (bodyNode in loopBodyNodes) {
                // Inject loop-scoped variables by augmenting node params.
                val enhancedParams = (bodyNode.params ?: emptyMap<String, Any>()) + mapOf<String, Any>(
                    "__loopItem" to (item ?: "null"),
                    "__loopIndex" to index
                )
                val enhancedNode = bodyNode.copy(params = enhancedParams)
                val record = engine.executeNode(enhancedNode, item, iterState, functionRegistry)
                if (record.output != null) {
                    iterState = iterState.withNodeOutput(bodyNode.id, record.output)
                }
            }

            // Iteration result is the output of the last body node, or the raw item if no body exists.
            val iterOutput = if (loopBodyNodes.isNotEmpty()) {
                iterState.getNodeOutput<Any?>(loopBodyNodes.last().id)
            } else {
                item
            }
            results.add(iterOutput)
            currentState = iterState
        }

        return NodeExecutionRecord.success(node, results, elapsedMs(startNano))
    }

    /**
     * Resolve the iterable collection for a LOOP node.
     *
     * Accepts three shapes, in order:
     *  - a literal `List` (params["collection"] = [1, 2, 3])
     *  - a reference map `{$ref: "nodeId"}` pulling an upstream node output
     *  - anything else (wrapped as a single-element list)
     */
    private fun resolveCollection(params: Map<String, Any>, state: ImmutableExecutionState): List<Any?> {
        val raw = params["collection"] ?: return emptyList()

        return when (raw) {
            is List<*> -> raw
            is Map<*, *> -> {
                val ref = raw["\$ref"]?.toString()
                if (ref != null) {
                    val output = state.getNodeOutput<Any?>(ref)
                    (output as? List<*>) ?: listOf(output)
                } else {
                    listOf(raw)
                }
            }
            else -> listOf(raw)
        }
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L
}
