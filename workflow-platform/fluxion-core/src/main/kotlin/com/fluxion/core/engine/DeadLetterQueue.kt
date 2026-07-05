package com.fluxion.core.engine

import com.fluxion.core.value.ExecutionMeta

/**
 * 死信队列 SPI — 捕获重试耗尽 + 补偿失败的终态失败记录
 *
 * 触发时机：
 *   1. 节点 errorStrategy=RETRY 且重试次数耗尽，最终仍 FAILED
 *   2. Saga 补偿链执行过程中补偿函数失败
 *   3. 工作流整体执行失败（如 outputSchema 校验不通过）
 *
 * 典型实现：
 *   - LoggingDeadLetterQueue（仅日志，默认实现）
 *   - DbDeadLetterQueue（持久化到 wf_dead_letter 表，支持 Admin 后台查看/重试）
 *   - MqDeadLetterQueue（投递到 Kafka/RabbitMQ DLQ topic，供外部消费者处理）
 *
 * 默认实现：NoopDeadLetterQueue（不记录，等效于关闭 DLQ）
 */
interface DeadLetterQueue {

    /**
     * 将失败记录投递到死信队列
     *
     * @param entry 死信条目（包含完整的失败上下文）
     */
    fun enqueue(entry: DeadLetterEntry)

    companion object {
        /** 空实现：不记录 */
        val NOOP: DeadLetterQueue = object : DeadLetterQueue {
            override fun enqueue(entry: DeadLetterEntry) {}
        }
    }
}

/**
 * 死信条目 — 记录一次终态失败的完整上下文
 *
 * @param executionId  执行 ID
 * @param workflowId   工作流 ID
 * @param workflowName 工作流名称
 * @param nodeId       失败节点 ID（工作流级失败时为 null）
 * @param nodeName     失败节点名称
 * @param errorMessage 错误信息
 * @param errorType    异常类名
 * @param failureKind  失败类型（NODE_FAILED / RETRY_EXHAUSTED / COMPENSATION_FAILED / WORKFLOW_FAILED）
 * @param inputs       工作流原始入参（用于排查和手动重试）
 * @param meta         执行元信息
 * @param enqueuedAt   入队时间戳（毫秒）
 */
data class DeadLetterEntry(
    val executionId: String,
    val workflowId: String,
    val workflowName: String,
    val nodeId: String?,
    val nodeName: String?,
    val errorMessage: String?,
    val errorType: String?,
    val failureKind: FailureKind,
    val inputs: Map<String, Any>,
    val meta: ExecutionMeta,
    val enqueuedAt: Long = System.currentTimeMillis()
) {
    /** 失败类型 */
    enum class FailureKind {
        /** 节点直接失败（errorStrategy=FAIL） */
        NODE_FAILED,
        /** 重试次数耗尽 */
        RETRY_EXHAUSTED,
        /** Saga 补偿函数执行失败 */
        COMPENSATION_FAILED,
        /** 工作流级失败（outputSchema 校验等） */
        WORKFLOW_FAILED
    }
}
