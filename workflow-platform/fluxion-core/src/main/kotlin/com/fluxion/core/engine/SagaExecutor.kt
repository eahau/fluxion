package com.fluxion.core.engine

import com.fluxion.core.exception.SagaExecutionException
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.model.*
import com.fluxion.core.value.EngineResult
import org.slf4j.*
import java.util.ArrayDeque

/**
 * Saga 执行器 — 基于 FunctionResult.sideEffects 的精确补偿事务
 *
 * 正向执行：每个节点完成后将副作用压栈
 * 失败补偿：逆序调用各节点的 compensateFunctionRef 撤销副作用
 */
class SagaExecutor(
    private val functionRegistry: FunctionRegistry,
    private val deadLetterQueue: DeadLetterQueue = DeadLetterQueue.NOOP
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 正向执行工作流（线性顺序，非 DAG）。
     *
     * 每个节点完成后将有副作用的节点压入补偿栈；
     * 任一节点失败时触发补偿链（逆序调用 compensateFunctionRef）。
     *
     * @param def      工作流定义
     * @param rawInput 原始入参
     * @return 成功时返回 EngineResult.success；失败时抛出 SagaExecutionException
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

                // 将有副作用的节点压入补偿栈
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
     * 逆序补偿链：从栈顶取出节点，调用其 compensateFunctionRef 撤销副作用。
     * 补偿失败仅记录日志，不中断后续补偿。
     */
    private fun compensate(stack: ArrayDeque<CompensationEntry>, state: ImmutableExecutionState) {
        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            if (entry.compensateFunctionRef == null) continue
            try {
                val compensateFn = functionRegistry.resolve(entry.compensateFunctionRef)
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

    /** 构建节点输入（组装 directInput + declaredDeps + workflowInput + nodeParams） */
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
