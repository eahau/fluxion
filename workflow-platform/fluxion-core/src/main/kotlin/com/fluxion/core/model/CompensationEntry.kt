package com.fluxion.core.model

import com.fluxion.core.value.SideEffect

/**
 * Saga 补偿栈条目 — 记录已执行节点的补偿信息
 */
data class CompensationEntry(
    /** 节点 ID */
    val nodeId: String,
    /** 节点名称 */
    val nodeName: String,
    /** 补偿函数引用 */
    val compensateFunctionRef: String?,
    /** 节点执行时的副作用（补偿时用于构建 compensate NodeInput） */
    val sideEffects: List<SideEffect>,
    /** 节点执行时的输出（补偿时用于构建 compensate directInput） */
    val output: Any?
)
