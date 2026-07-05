package com.fluxion.core.log

import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.NodeExecutionRecord

/**
 * 执行日志存储 SPI — 解耦引擎与存储介质
 *
 * 默认实现：LoggingExecutionLogStore（结构化 JSON 日志，在 workflow-admin 中提供）
 * 可选实现：DbExecutionLogStore（DB 持久化，需建 wf_execution_log 表）
 */
interface ExecutionLogStore {

    /** 是否启用（false 时 WorkflowEngine 跳过 save 调用） */
    fun enabled(): Boolean

    /** 保存单个节点执行记录 */
    fun save(record: NodeExecutionRecord, meta: ExecutionMeta)

    /**
     * 按 executionId 加载完整执行轨迹（调试/重放用）
     * LoggingExecutionLogStore 不支持此操作，返回空列表；
     * 需要精确重放时必须启用 DbExecutionLogStore
     */
    fun loadTrace(executionId: String): List<NodeExecutionRecord> = emptyList()
}
