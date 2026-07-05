package com.fluxion.core.engine.control

import com.fluxion.core.engine.WorkflowEngine
import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.value.NodeExecutionRecord
import org.slf4j.*

/**
 * 循环节点执行器 — 遍历集合，对每个元素执行一组子节点
 *
 * 节点参数（params）配置：
 * - collection: 待遍历的集合（List 或 SpEL 表达式引用上游输出）
 * - iteratorVar: 迭代变量名（默认 "item"），注入到子节点的 nodeParams["__loopItem"]
 * - maxIterations: 最大迭代次数上限保护（默认 1000）
 *
 * 输出：所有迭代的输出聚合为 List
 *
 * 用法示例（节点 JSON 配置）：
 * ```json
 * {
 *   "id": "loop1",
 *   "type": "LOOP",
 *   "params": {
 *     "collection": [1, 2, 3],
 *     "iteratorVar": "item",
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
     * 执行循环节点。
     *
     * @param node       LOOP 类型节点
     * @param state      当前不可变执行状态快照
     * @param mockConfig 调试 Mock 配置
     * @param allNodes   工作流全部节点列表（用于解析 loopBody 中的子节点引用）
     * @return 节点执行记录（output = 聚合后的结果列表）
     */
    suspend fun execute(
        node: WorkflowNode,
        state: ImmutableExecutionState,
        mockConfig: MockConfig,
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

        // 解析 loopBody 子节点 ID 列表
        val loopBodyIds = (params["loopBody"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val nodeIndex = allNodes.associateBy { it.id }
        val loopBodyNodes = loopBodyIds.mapNotNull { nodeIndex[it] }

        val results = mutableListOf<Any?>()
        var currentState = state
        val iterations = minOf(collection.size, maxIterations)

        for ((index, item) in collection.withIndex().take(iterations)) {
            log.debug { "Loop node [${node.name}]: iteration ${index + 1}/$iterations" }

            // 为每个迭代创建带 __loopItem 和 __loopIndex 的增强节点
            var iterState = currentState
            for (bodyNode in loopBodyNodes) {
                val enhancedParams = (bodyNode.params ?: emptyMap<String, Any>()) + mapOf<String, Any>(
                    "__loopItem" to (item ?: "null"),
                    "__loopIndex" to index
                )
                val enhancedNode = bodyNode.copy(
                    params = enhancedParams
                )
                val record = engine.executeNode(enhancedNode, item, iterState, mockConfig)
                if (record.output != null) {
                    iterState = iterState.withNodeOutput(bodyNode.id, record.output)
                }
            }

            // 取最后一个 body 节点的输出作为本次迭代结果
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
     * 解析待遍历的集合。
     *
     * 支持：
     * - 直接 List：params["collection"] = [1, 2, 3]
     * - 引用上游节点输出：params["collection"] = { "$ref": "nodeId" }
     * - 从 state 中取值
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
