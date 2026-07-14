package com.fluxion.core.engine.control

import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.engine.WorkflowDefinitionLoader
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.FunctionResolver
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.NodeExecutionRecord
import org.slf4j.*

/**
 * Executor for SUB_WORKFLOW nodes: invokes another Fluxion workflow definition
 * as a nested call with configurable depth guards and input mapping.
 *
 * Node params (from [WorkflowNode.params]):
 *  - `workflowId` (required) — target workflow definition to load and execute.
 *  - `maxDepth` (optional, default [DEFAULT_MAX_DEPTH]) — recursion safety
 *    cap. Each level increments a private `__subWorkflowDepth` counter; if
 *    the counter hits `maxDepth` the call is rejected to prevent runaway
 *    recursive cycles.
 *  - `inputMapping` (optional) — `{targetKey: sourcePath}` map applied to
 *    build the child workflow's input; if absent the raw parent
 *    `directInput` or `state.inputs` is forwarded instead. Source paths
 *    support `$.directInput.<field>`, `$.nodeOutputs.<nodeId>` and
 *    `$.inputs.<field>` prefixes, or plain literal values.
 */
class SubWorkflowExecutor(
    private val engine: WorkflowEngine,
    private val definitionLoader: WorkflowDefinitionLoader,
    private val dagExecutorProvider: () -> DagExecutor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DEFAULT_MAX_DEPTH = 10
        private const val DEPTH_KEY = "__subWorkflowDepth"
    }

    private fun elapsedMs(startNano: Long): Long =
        (System.nanoTime() - startNano) / 1_000_000L

    /**
     * Execute a SUB_WORKFLOW node.
     *
     * Loads the target definition, validates the depth safety cap, builds
     * the child input map, then delegates to a fresh [DagExecutor] for the
     * child DAG. Results from the child DAG become the node's output;
     * child failures are captured and reported via a failed
     * [NodeExecutionRecord] (the caller then applies the parent node's
     * [ErrorStrategy]).
     */
    suspend fun execute(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        functionRegistry: FunctionResolver
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        val params = node.params ?: emptyMap()

        val targetWorkflowId = params["workflowId"]?.toString()
            ?: return NodeExecutionRecord.failed(
                node,
                InvalidParamException("SUB_WORKFLOW node [${node.name}] missing required param: workflowId"),
                elapsedMs(startNano)
            )

        val currentDepth = (params[DEPTH_KEY] as? Number)?.toInt() ?: 0
        val maxDepth = (params["maxDepth"] as? Number)?.toInt() ?: DEFAULT_MAX_DEPTH
        if (currentDepth >= maxDepth) {
            return NodeExecutionRecord.failed(
                node,
                WorkflowNodeException.timeout(
                    "Sub-workflow nesting depth exceeded: $currentDepth >= $maxDepth (workflow=$targetWorkflowId)",
                    null
                ),
                elapsedMs(startNano)
            )
        }

        val childDef = definitionLoader.load(targetWorkflowId)
            ?: return NodeExecutionRecord.failed(
                node,
                InvalidParamException("Sub-workflow definition not found: $targetWorkflowId"),
                elapsedMs(startNano)
            )

        log.info {
            "Sub-workflow [${node.name}]: calling [$targetWorkflowId] v${childDef.version} " +
                "(depth=${currentDepth + 1}/$maxDepth)"
        }

        val childInput = buildChildInput(params, directInput, state)

        return try {
            val dagExecutor = dagExecutorProvider()
            val result = dagExecutor.execute(childDef, childInput, functionRegistry)

            if (result.success) {
                log.info {
                    "Sub-workflow [${node.name}]: [$targetWorkflowId] completed successfully, " +
                        "duration=${elapsedMs(startNano)}ms"
                }
                NodeExecutionRecord.success(node, result.data, elapsedMs(startNano))
            } else {
                val errorMsg = result.errorMsg ?: "Sub-workflow [$targetWorkflowId] failed"
                log.warn { "Sub-workflow [${node.name}]: [$targetWorkflowId] failed: $errorMsg" }
                NodeExecutionRecord.failed(
                    node,
                    WorkflowNodeException.timeout(errorMsg, null),
                    elapsedMs(startNano)
                )
            }
        } catch (e: Exception) {
            log.error(e) { "Sub-workflow [${node.name}]: [$targetWorkflowId] execution error" }
            NodeExecutionRecord.failed(node, e, elapsedMs(startNano))
        }
    }

    /**
     * Build the input map for the child workflow invocation.
     *
     * Priority when no explicit `inputMapping` is configured:
     *  1. If `directInput` is a Map, forward it directly.
     *  2. Else if `directInput` is null, fall back to the parent state's inputs.
     *  3. Else wrap the scalar into `{"input": directInput}`.
     *
     * When `inputMapping` is present each `{target: source}` entry is
     * resolved individually; null values are omitted so the child can
     * distinguish "not supplied" from "explicitly set to null".
     */
    private fun buildChildInput(
        params: Map<String, Any>,
        directInput: Any?,
        state: ImmutableExecutionState
    ): Map<String, Any> {
        val inputMapping = params["inputMapping"].uncheckedCast<Map<String, Any>>()

        if (inputMapping.isNullOrEmpty()) {
            return when (directInput) {
                is Map<*, *> -> directInput.uncheckedCast<Map<String, Any>>()!!
                null -> state.inputs
                else -> mapOf("input" to directInput)
            }
        }

        val result = mutableMapOf<String, Any>()
        for ((key, mapping) in inputMapping) {
            val value = resolveMappingValue(mapping, directInput, state)
            if (value != null) {
                result[key] = value
            }
        }
        return result
    }

    /**
     * Resolve one `inputMapping` source value.
     *
     * Non-string mappings are treated as literal pass-through. String
     * mappings are checked against the three supported prefixes; strings
     * that do not match any prefix are returned verbatim (so callers can
     * still pass literal string configuration even without the explicit
     * literal rule).
     */
    private fun resolveMappingValue(
        mapping: Any,
        directInput: Any?,
        state: ImmutableExecutionState
    ): Any? {
        if (mapping !is String) return mapping

        return when {
            mapping.startsWith("$.directInput.") -> {
                val field = mapping.removePrefix("$.directInput.")
                directInput.uncheckedCast<Map<String, Any>>()?.get(field)
            }
            mapping.startsWith("$.nodeOutputs.") -> {
                val nodeId = mapping.removePrefix("$.nodeOutputs.")
                state.getNodeOutput<Any?>(nodeId)
            }
            mapping.startsWith("$.inputs.") -> {
                val field = mapping.removePrefix("$.inputs.")
                state.inputs[field]
            }
            else -> mapping
        }
    }
}
