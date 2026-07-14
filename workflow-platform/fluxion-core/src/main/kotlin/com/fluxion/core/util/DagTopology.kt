package com.fluxion.core.util

import com.fluxion.core.exception.CyclicDependencyException
import com.fluxion.core.exception.DuplicateNodeIdException
import com.fluxion.core.model.WorkflowNode

/**
 * DAG (directed acyclic graph) validation and topological sorting for
 * workflow node graphs.
 *
 * Two entry points:
 *  - [validate] — full structural check (unique ids, no dangling
 *    references, no cycles) used at publish-time.  Throws typed
 *    exceptions so the admin console can render precise error UI.
 *  - [topologicalSort] — hot-path Kahn sort used at the start of every
 *    execution.  Returns `null` on cycle instead of throwing because
 *    hot-path callers prefer a graceful degrade + fallback to linear
 *    order rather than a fatal exception.
 */
object DagTopology {

    /**
     * Result wrapper from [validate].
     *
     * @property sortedIds nodes in a valid topological execution order.
     */
    data class Result(val sortedIds: List<String>)

    /**
     * Full structural validation of a workflow definition graph.
     *
     * Performs three checks in order:
     *  1. Every node id is unique.
     *  2. Every `dependsOn` reference resolves to an existing node.
     *  3. The graph contains no cycles (topological sort covers all nodes).
     *
     * @param workflowId owning definition id — attached to exception messages.
     * @param nodes      the graph to validate.
     * @return sorted node ids if valid.
     * @throws DuplicateNodeIdException if any node id repeats.
     * @throws CyclicDependencyException if the graph contains a cycle.
     * @throws IllegalArgumentException if a node references a non-existent dep.
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
                        "Node [${node.id}] depends on [$dep] which does not exist in workflow [$workflowId]"
                    )
                }
            }
        }

        val sorted = topologicalSort(nodes)
            ?: throw CyclicDependencyException(workflowId)
        return Result(sorted)
    }

    /**
     * Kahn's algorithm — produces a topological order of the node ids
     * from a list of [WorkflowNode] whose edges are carried in
     * [WorkflowNode.dependsOn].
     *
     * Returns `null` if the graph has a cycle (i.e. the resulting
     * order contains fewer nodes than the input).  The algorithm is
     * O(V + E) and allocation-light on hot paths because it reuses
     * mutable maps built once per call.
     *
     * @return topologically-sorted node ids, or `null` on cycle.
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
