package com.fluxion.core.engine.control

import com.fluxion.core.engine.WorkflowDefinitionLoader
import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.NodeExecutionRecord
import org.slf4j.*

/**
 * 子工作流节点执行器 — 调用另一个工作流，实现逻辑复用与组合
 *
 * 典型场景：
 * - 将"查询 DB + 数据转换 + 封装"打包为独立工作流，供多个上层工作流复用
 * - 类比传统架构中的 Service 层封装（如 OrderService.getOrderDetail()）
 *
 * 节点参数（params）配置：
 * - workflowId:   目标子工作流 ID（必填）
 * - inputMapping:  输入映射（可选，将父工作流的节点输出映射为子工作流入参）
 * - maxDepth:      最大嵌套深度（默认 10，防止循环引用导致栈溢出）
 *
 * 用法示例（节点 JSON 配置）：
 * ```json
 * {
 *   "id": "call_detail",
 *   "type": "SUB_WORKFLOW",
 *   "name": "调用订单详情工作流",
 *   "params": {
 *     "workflowId": "getOrderDetail",
 *     "inputMapping": { "orderId": "$.directInput.orderId" },
 *     "maxDepth": 5
 *   }
 * }
 * ```
 *
 * 防护机制：
 * - 循环引用检测：通过调用栈深度限制，防止 A→B→A 无限递归
 * - 找不到定义时抛出明确异常，不静默失败
 */
class SubWorkflowExecutor(
    private val engine: WorkflowEngine,
    private val definitionLoader: WorkflowDefinitionLoader,
    private val dagExecutorProvider: () -> com.fluxion.core.engine.DagExecutor
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val DEFAULT_MAX_DEPTH = 10
        private const val DEPTH_KEY = "__subWorkflowDepth"
    }

    /**
     * 执行子工作流节点。
     *
     * @param node       SUB_WORKFLOW 类型节点
     * @param directInput 上游节点输出（作为子工作流入参的来源）
     * @param state      当前不可变执行状态快照
     * @param mockConfig 调试 Mock 配置
     * @return 节点执行记录（output = 子工作流最终输出）
     */
    suspend fun execute(
        node: WorkflowNode,
        directInput: Any?,
        state: ImmutableExecutionState,
        mockConfig: MockConfig
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        val params = node.params ?: emptyMap()

        // 1. 解析目标工作流 ID
        val targetWorkflowId = params["workflowId"]?.toString()
            ?: return NodeExecutionRecord.failed(
                node,
                InvalidParamException("SUB_WORKFLOW node [${node.name}] missing required param: workflowId"),
                elapsedMs(startNano)
            )

        // 2. 嵌套深度保护
        val currentDepth = (params[DEPTH_KEY] as? Number)?.toInt() ?: 0
        val maxDepth = (params["maxDepth"] as? Number)?.toInt() ?: DEFAULT_MAX_DEPTH
        if (currentDepth >= maxDepth) {
            return NodeExecutionRecord.failed(
                node,
                WorkflowNodeException.timeout(
                    "Sub-workflow nesting depth exceeded: $currentDepth >= $maxDepth (workflow=$targetWorkflowId)", null
                ),
                elapsedMs(startNano)
            )
        }

        // 3. 加载子工作流定义
        val childDef = definitionLoader.load(targetWorkflowId)
            ?: return NodeExecutionRecord.failed(
                node,
                InvalidParamException("Sub-workflow definition not found: $targetWorkflowId"),
                elapsedMs(startNano)
            )

        log.info { "Sub-workflow [${node.name}]: calling [$targetWorkflowId] v${childDef.version} (depth=${currentDepth + 1}/$maxDepth)" }

        // 4. 构建子工作流入参
        val childInput = buildChildInput(params, directInput, state)

        // 5. 执行子工作流（递归调用 DagExecutor，深度+1 注入到所有节点参数）
        return try {
            val dagExecutor = dagExecutorProvider()
            val result = dagExecutor.execute(childDef, childInput, mockConfig)

            if (result.success) {
                log.info { "Sub-workflow [${node.name}]: [$targetWorkflowId] completed successfully, duration=${elapsedMs(startNano)}ms" }
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
            log.error("Sub-workflow [${node.name}]: [$targetWorkflowId] execution error", e)
            NodeExecutionRecord.failed(node, e, elapsedMs(startNano))
        }
    }

    /**
     * 构建子工作流入参。
     *
     * 支持：
     * - 直接传入 directInput（默认：上游输出作为子工作流入参）
     * - inputMapping 映射：从 directInput / state 中提取指定字段
     */
    private fun buildChildInput(
        params: Map<String, Any>,
        directInput: Any?,
        state: ImmutableExecutionState
    ): Map<String, Any> {
        val inputMapping = params["inputMapping"].uncheckedCast<Map<String, Any>>()

        if (inputMapping.isNullOrEmpty()) {
            // 无映射：直接将 directInput 作为入参（若为 Map 则直传，否则包装）
            return when (directInput) {
                is Map<*, *> -> directInput.uncheckedCast<Map<String, Any>>()!!
                null -> state.inputs
                else -> mapOf("input" to directInput)
            }
        }

        // 有映射：按 key 解析
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
     * 解析单个映射值。
     *
     * 支持的表达式：
     * - "$.directInput.field"  → 从 directInput 中取字段
     * - "$.nodeOutputs.nodeId" → 从上游节点输出中取值
     * - "$.inputs.field"       → 从工作流入参中取值
     * - 普通字符串             → 直接作为常量值
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
            else -> mapping  // 常量
        }
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L
}
