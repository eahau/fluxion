package com.fluxion.core.util

import com.fluxion.core.exception.CyclicDependencyException
import com.fluxion.core.exception.DuplicateNodeIdException
import com.fluxion.core.model.WorkflowNode

/**
 * DAG 拓扑工具。
 *
 * 集中处理拓扑排序、环检测、重复节点 ID 与悬空依赖检查，
 * 供执行器与配置时校验共用。
 */
object DagTopology {

    /**
     * 拓扑校验结果。
     *
     * @property sortedIds 拓扑排序后的节点 ID 列表
     */
    data class Result(val sortedIds: List<String>)

    /**
     * 校验并拓扑排序。
     *
     * 校验项：
     * 1. 节点 ID 不能重复
     * 2. dependsOn 引用的节点必须存在
     * 3. DAG 不能成环
     *
     * @param workflowId 工作流 ID，用于异常信息
     * @param nodes 节点列表
     * @return 拓扑排序结果
     * @throws DuplicateNodeIdException 存在重复节点 ID
     * @throws CyclicDependencyException 存在循环依赖
     * @throws IllegalArgumentException 依赖了不存在的节点
     */
    fun validate(workflowId: String, nodes: List<WorkflowNode>): Result {
        val duplicate = nodes.groupingBy { it.id }.eachCount().entries.find { it.value > 1 }?.key
        if (duplicate != null) {
            throw DuplicateNodeIdException(duplicate)
        }

        val nodeIds = nodes.map { it.id }.toSet()
        nodes.forEach { node ->
            node.dependsOn?.forEach { dep ->
                if (dep !in nodeIds) {
                    throw IllegalArgumentException(
                        "节点 [${node.id}] 依赖了不存在的节点 [$dep]"
                    )
                }
            }
        }

        val sorted = topologicalSort(nodes)
            ?: throw CyclicDependencyException(workflowId)
        return Result(sorted)
    }

    /**
     * Kahn 算法拓扑排序。
     *
     * @return 拓扑有序节点 ID 列表；若存在环则返回 null
     */
    fun topologicalSort(nodes: List<WorkflowNode>): List<String>? {
        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for (node in nodes) {
            inDegree.putIfAbsent(node.id, 0)
            adjacency.putIfAbsent(node.id, mutableListOf())
            node.dependsOn?.forEach { dep ->
                inDegree[node.id] = (inDegree[node.id] ?: 0) + 1
                adjacency.getOrPut(dep) { mutableListOf() }.add(node.id)
            }
        }

        val queue = ArrayDeque(nodes.filter { (inDegree[it.id] ?: 0) == 0 }.map { it.id })
        val result = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            adjacency[current]?.forEach { successor ->
                val newDegree = (inDegree[successor] ?: 1) - 1
                inDegree[successor] = newDegree
                if (newDegree == 0) queue.add(successor)
            }
        }

        return if (result.size == nodes.size) result else null
    }
}
