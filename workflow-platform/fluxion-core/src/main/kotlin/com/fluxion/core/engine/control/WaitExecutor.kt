package com.fluxion.core.engine.control

import com.fluxion.core.model.ImmutableExecutionState
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.signal.Signal
import com.fluxion.core.signal.SignalBroker
import com.fluxion.core.value.NodeExecutionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.*

/**
 * 等待节点执行器 — 暂停工作流执行，等待外部信号恢复
 *
 * 典型场景：
 * - 人工审批：等待管理员审批/拒绝信号
 * - 外部回调：等待第三方系统回调通知
 * - 定时等待：等待指定时间后自动恢复
 *
 * 节点参数（params）配置：
 * - signalName: 等待的信号名称（默认 "default"）
 * - waitTimeoutMs: 等待超时毫秒数（0=无限等待，默认 0）
 * - timeoutOutput: 超时时返回的默认输出（默认 null）
 *
 * 用法示例（节点 JSON 配置）：
 * ```json
 * {
 *   "id": "wait_approval",
 *   "type": "WAIT",
 *   "name": "等待审批",
 *   "params": {
 *     "signalName": "approval",
 *     "waitTimeoutMs": 86400000,
 *     "timeoutOutput": { "status": "TIMEOUT" }
 *   }
 * }
 * ```
 *
 * 恢复方式：
 * 通过 Admin API 发送信号：
 * ```
 * POST /api/admin/executions/{executionId}/signal
 * { "signalName": "approval", "data": { "approved": true } }
 * ```
 */
class WaitExecutor(
    private val signalBroker: SignalBroker
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 执行等待节点。
     *
     * 挂起当前协程，通过 [SignalBroker] 等待外部信号。
     * 信号到达后以信号 payload 作为节点输出；
     * 超时后返回 timeoutOutput 或 null。
     *
     * @param node  WAIT 类型节点
     * @param state 当前不可变执行状态快照
     * @return 节点执行记录
     */
    suspend fun execute(
        node: WorkflowNode,
        state: ImmutableExecutionState
    ): NodeExecutionRecord {
        val startNano = System.nanoTime()
        val params = node.params ?: emptyMap()

        val signalName = params["signalName"]?.toString() ?: "default"
        val waitTimeoutMs = (params["waitTimeoutMs"] as? Number)?.toLong() ?: 0L
        val timeoutOutput = params["timeoutOutput"]

        val executionId = state.meta.executionId

        log.info { "Wait node [${node.name}]: waiting for signal '$signalName' (executionId=$executionId, timeout=${waitTimeoutMs}ms)" }

        // 在 IO 调度器上阻塞等待信号（桥接 CompletableFuture → 协程）
        val signal: Signal? = withContext(Dispatchers.IO) {
            signalBroker.awaitSignal(executionId, signalName, waitTimeoutMs)
        }

        val elapsedMs = elapsedMs(startNano)

        return if (signal != null) {
            log.info { "Wait node [${node.name}]: received signal '$signalName', duration=${elapsedMs}ms" }
            NodeExecutionRecord.success(
                node = node,
                output = signal.payload ?: mapOf("signalName" to signal.name, "status" to "RECEIVED"),
                durationMs = elapsedMs
            )
        } else {
            log.info { "Wait node [${node.name}]: signal '$signalName' timed out after ${waitTimeoutMs}ms" }
            if (timeoutOutput != null) {
                NodeExecutionRecord.success(node, timeoutOutput, elapsedMs)
            } else {
                NodeExecutionRecord.success(
                    node = node,
                    output = mapOf("signalName" to signalName, "status" to "TIMEOUT"),
                    durationMs = elapsedMs
                )
            }
        }
    }

    private fun elapsedMs(startNano: Long): Long = (System.nanoTime() - startNano) / 1_000_000L
}
