package com.fluxion.core.model

import com.fluxion.core.value.ExecutionMeta
import java.util.UUID

/**
 * 工作流执行状态 — 不可变快照，类比"调用栈的只读视图"
 *
 * 线程安全：所有字段不可变，天然支持 DAG 并发读取，无需任何同步
 */
class ImmutableExecutionState private constructor(
    /** 工作流原始入参（类比函数调用时的实参，永远不变） */
    val inputs: Map<String, Any>,
    /** 已执行节点的输出快照（Append-only） */
    val nodeOutputs: Map<String, Any?>,
    /** 执行元信息 */
    val meta: ExecutionMeta
) {
    companion object {
        /** 工作流启动时创建初始状态 */
        @JvmStatic
        fun start(def: WorkflowDefinition, rawInput: Map<String, Any>?): ImmutableExecutionState {
            // 将定义级 appGroup/workflowId 注入到每个节点，供装饰器生成规范 Redis Key
            def.nodes.forEach { node ->
                node.workflowId = def.id
                if (node.appGroup.isNullOrBlank()) {
                    node.appGroup = def.appGroup
                }
            }
            val meta = ExecutionMeta(
                workflowId = def.id,
                workflowName = def.name,
                version = def.version,
                appGroup = def.appGroup,
                executionId = UUID.randomUUID().toString(),
                startTime = System.currentTimeMillis()
            )
            return ImmutableExecutionState(
                inputs = rawInput?.toMap() ?: emptyMap(),
                nodeOutputs = emptyMap(),
                meta = meta
            )
        }
    }

    /** 节点完成后产生新快照（不修改当前实例） */
    fun withNodeOutput(nodeId: String, output: Any?): ImmutableExecutionState =
        ImmutableExecutionState(inputs, nodeOutputs + (nodeId to output), meta)

    /** DAG 并行节点完成后批量合并（原子操作） */
    fun mergeNodeOutputs(parallelOutputs: Map<String, Any?>): ImmutableExecutionState =
        ImmutableExecutionState(inputs, nodeOutputs + parallelOutputs, meta)

    // ─── 只读访问接口 ───────────────────────────────────────────

    inline fun <reified T> getInput(key: String): T? = inputs[key] as? T

    inline fun <reified T> getNodeOutput(nodeId: String): T? = nodeOutputs[nodeId] as? T

    fun hasNodeOutput(nodeId: String): Boolean = nodeOutputs.containsKey(nodeId)
}
