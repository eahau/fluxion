package com.fluxion.core.debug

import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.mock.MockConfig
import com.fluxion.core.mock.MockRule
import com.fluxion.core.value.NodeExecutionRecord

/**
 * 调试上下文 — 持有调试会话的运行时状态。
 *
 * 包含当前执行状态、Mock 配置、断点集合、执行轨迹和下一节点索引。
 * 可通过 [toSnapshot] 转换为可序列化的 [DebugSnapshot]，实现无状态前后端交互。
 */
class DebugContext(
    /** 当前不可变执行状态 */
    val executionState: ImmutableExecutionState,
    /** Mock 配置（可动态修改） */
    var mockConfig: MockConfig,
    /** 断点节点 ID 集合 */
    val breakpoints: Set<String>,
    /** 已执行节点的轨迹记录 */
    val traces: List<NodeExecutionRecord>,
    /** 下一个待执行节点的索引 */
    var nextNodeIndex: Int
) {
    /** 推进到下一个节点 */
    fun advanceNextNode() { nextNodeIndex++ }

    /** 添加/更新 Mock 规则 */
    fun addMockRule(rule: MockRule) {
        val rules = mockConfig.rules.toMutableList()
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            rules[idx] = rule
        } else {
            rules.add(rule)
        }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** 移除 Mock 规则 */
    fun removeMockRule(ruleId: String) {
        val rules = mockConfig.rules.filterNot { it.id == ruleId }
        this.mockConfig = mockConfig.copy(rules = rules)
    }

    /** 是否启用 Mock */
    fun setMockEnabled(enabled: Boolean) {
        this.mockConfig = mockConfig.copy(enabled = enabled)
    }

    /** 转换为可序列化的快照（用于前后端传输） */
    fun toSnapshot(workflowId: String, workflowVersion: Int, pausedAtNodeId: String?): DebugSnapshot =
        DebugSnapshot(
            workflowId = workflowId,
            workflowVersion = workflowVersion,
            executionState = executionState,
            traces = traces,
            breakpoints = breakpoints,
            mockConfig = mockConfig,
            pausedAtNodeId = pausedAtNodeId,
            nextNodeIndex = nextNodeIndex
        )
}

/**
 * 调试状态快照 v3。
 *
 * 不可变、可序列化的调试会话状态，用于前后端传输。
 * 通过 [DebugSnapshot.restore] 可无状态恢复为 [DebugContext]。
 */
data class DebugSnapshot(
    /** 工作流定义 ID */
    val workflowId: String,
    /** 工作流版本号（恢复时校验一致性） */
    val workflowVersion: Int,
    /** 不可变执行状态（节点输出、输入、元信息） */
    val executionState: ImmutableExecutionState,
    /** 已执行节点的轨迹记录 */
    val traces: List<NodeExecutionRecord>,
    /** 断点节点 ID 集合 */
    val breakpoints: Set<String>,
    /** Mock 配置 */
    val mockConfig: MockConfig,
    /** 当前暂停的节点 ID（null 表示未暂停） */
    val pausedAtNodeId: String?,
    /** 下一个待执行节点的索引 */
    val nextNodeIndex: Int
) {
    companion object {
        /** 从 DebugSnapshot 恢复调试上下文（无服务端 Session） */
        @JvmStatic
        fun restore(snap: DebugSnapshot, def: WorkflowDefinition): DebugContext {
            check(snap.workflowVersion == def.version) {
                "Workflow version mismatch: snapshot=${snap.workflowVersion}, definition=${def.version}"
            }
            return DebugContext(
                snap.executionState, snap.mockConfig,
                snap.breakpoints, snap.traces, snap.nextNodeIndex
            )
        }
    }
}
