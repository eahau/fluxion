package com.fluxion.core.engine

import com.fluxion.core.exception.SagaExecutionException
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.model.*
import com.fluxion.core.value.EngineResult
import org.slf4j.*
import java.util.ArrayDeque

/**
 * Saga pattern executor: walks nodes in order and, on any failure, reverses
 * previously committed side effects by running the declared compensation
 * functions in reverse order.
 *
 * Side effects are reported by individual functions via
 * [FunctionResult.sideEffects]; nodes that *declare* a
 * [WorkflowNode.compensateFunctionRef] are pushed to the compensation stack
 * regardless so the Saga coordinator can always reach the compensation
 * function even if the node produced no explicit side-effect records.
 *
 * Failure semantics: the first exception aborts the forward pass and
 * immediately triggers the compensation chain; the original error is then
 * rethrown as a [SagaExecutionException] for the caller to translate into
 * an HTTP/RPC error.
 */
class SagaExecutor(
    private val functionRegistry: FunctionRegistry,
    private val deadLetterQueue: DeadLetterQueue = DeadLetterQueue.NOOP
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Run a workflow as a Saga (forward pass + compensation on failure).
     *
     * @param def workflow definition — its `nodes` list is walked in order.
     * @param rawInput workflow-level inputs passed to each node.
     * @return [EngineResult.success] on happy path.
     * @throws SagaExecutionException wrapping the original node error (after
     *   the compensation chain has been fully executed).
     */
    fun execute(def: WorkflowDefinition, rawInput: Map<String, Any>?): EngineResult {
        var state = ImmutableExecutionState.start(def, rawInput)
        val lockedNodes = def.nodes.toMutableList()
        val compensationStack = ArrayDeque<CompensationEntry>()

        lockedNodes.forEach { it.workflowId = def.id }

        var directInput: Any? = rawInput

        for (node in lockedNodes) {
            try {
                val nodeInput = buildNodeInput(node, directInput, state)
                val fn = functionRegistry.resolve(node.functionRef, node.type)
                val result = fn.apply(nodeInput)

                state = state.withNodeOutput(node.id, result.output)
                directInput = result.output

                // Push nodes that produced side effects onto the stack for rollback.
                if (result.sideEffects.isNotEmpty()) {
                    compensationStack.push(
                        CompensationEntry(
                            nodeId = node.id,
                            nodeName = node.name,
                            compensateFunctionRef = node.compensateFunctionRef,
                            sideEffects = result.sideEffects,
                            output = result.output
                        )
                    )
                }
            } catch (ex: Exception) {
                log.error(ex) { "Saga failed at node [${node.id}], triggering compensation chain" }
                compensate(compensationStack, state)
                throw SagaExecutionException("Saga failed at [${node.id}]: ${ex.message}", ex)
            }
        }

        return EngineResult.success(directInput, state)
    }

    /**
     * Walk the compensation stack (LIFO) invoking each declared
     * `compensateFunctionRef` with the node's last output and side effects.
     *
     * Compensation failures do NOT abort the chain — each failing entry is
     * enqueued into the [DeadLetterQueue] so operators can manually
     * reconcile later.
     */
    private fun compensate(stack: ArrayDeque<CompensationEntry>, state: ImmutableExecutionState) {
        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val compensateRef = entry.compensateFunctionRef ?: continue
            try {
                val compensateFn = functionRegistry.resolve(compensateRef)
                val compensateInput = NodeInput(
                    directInput = entry.output,
                    declaredDeps = emptyMap(),
                    workflowInput = state.inputs,
                    nodeParams = mapOf("sideEffects" to entry.sideEffects),
                    meta = state.meta
                )
                compensateFn.apply(compensateInput)
                log.info { "Saga compensation OK: node=[${entry.nodeId}]" }
            } catch (e: Exception) {
                log.error(e) { "Saga compensation FAILED for node=[${entry.nodeId}]: ${e.message}" }
                try {
                    deadLetterQueue.enqueue(
                        DeadLetterEntry(
                            executionId = state.meta.executionId,
                            workflowId = state.meta.workflowId,
                            workflowName = state.meta.workflowName,
                            nodeId = entry.nodeId,
                            nodeName = entry.nodeName,
                            errorMessage = e.message,
                            errorType = e::class.java.simpleName,
                            failureKind = DeadLetterEntry.FailureKind.COMPENSATION_FAILED,
                            inputs = state.inputs,
                            meta = state.meta
                        )
                    )
                } catch (dlqEx: Exception) {
                    log.warn(dlqEx) { "Failed to enqueue DLQ for compensation failure node=[${entry.nodeId}]" }
                }
            }
        }
    }

    /** Build the NodeInput passed to each forward-pass node. */
    private fun buildNodeInput(node: WorkflowNode, directInput: Any?, state: ImmutableExecutionState): NodeInput {
        val declaredDeps = linkedMapOf<String, Any>()
        node.dependsOn?.forEach { depNodeId ->
            state.getNodeOutput<Any>(depNodeId)?.let { declaredDeps[depNodeId] = it }
        }
        return NodeInput(
            directInput = directInput,
            declaredDeps = declaredDeps.toMap(),
            workflowInput = state.inputs,
            nodeParams = node.params ?: emptyMap(),
            meta = state.meta
        )
    }
}
