package com.fluxion.core.engine

import com.fluxion.core.signal.SignalBroker
import java.util.concurrent.ConcurrentHashMap

/**
 * 运行中的工作流执行信息
 *
 * 由 [ExecutionTracker] 管理，供 Query API 和 Signal API 查询使用。
 */
data class RunningExecution(
    val executionId: String,
    val workflowId: String,
    val workflowName: String,
    val version: Int,
    val startTime: Long,
    val inputs: Map<String, Any>,
    val nodeIds: List<String>
)

/**
 * 运行态执行追踪器 — 追踪当前正在执行的工作流实例
 *
 * 类比 Temporal 的 visibility store（轻量版），提供：
 * - 运行中工作流列表查询
 * - 单次执行状态查询（用于 Query API）
 * - 与 [com.fluxion.core.signal.SignalBroker] 配合实现 Signal/Query
 *
 * 线程安全：基于 ConcurrentHashMap，适用于 DAG 并发执行场景。
 */
class ExecutionTracker(
    private val signalBroker: SignalBroker? = null
) {

    private val running = ConcurrentHashMap<String, RunningExecution>()

    /**
     * 标记执行开始（DagExecutor.execute 入口调用）
     */
    fun trackStart(
        executionId: String,
        workflowId: String,
        workflowName: String,
        version: Int,
        inputs: Map<String, Any>,
        nodeIds: List<String>
    ) {
        running[executionId] = RunningExecution(
            executionId = executionId,
            workflowId = workflowId,
            workflowName = workflowName,
            version = version,
            startTime = System.currentTimeMillis(),
            inputs = inputs,
            nodeIds = nodeIds
        )
    }

    /**
     * 标记执行结束（DagExecutor.execute finally 块调用）
     */
    fun trackEnd(executionId: String) {
        running.remove(executionId)
        signalBroker?.cleanup(executionId)
    }

    /**
     * 获取指定执行的运行态信息
     */
    fun getRunning(executionId: String): RunningExecution? = running[executionId]

    /**
     * 列出所有运行中的执行
     */
    fun listRunning(): List<RunningExecution> = running.values.toList()

    /**
     * 判断指定执行是否正在运行
     */
    fun isRunning(executionId: String): Boolean = running.containsKey(executionId)

    /**
     * 当前运行中的执行数量
     */
    fun size(): Int = running.size
}
