package com.fluxion.adapter.mq.kafka

import com.fluxion.adapter.spi.UnifiedRequest
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.adapter.spi.mq.MqPublisher
import com.fluxion.core.exception.WorkflowException
import com.fluxion.core.util.JsonUtil
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.*
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Kafka 工作流事件消费者（基于原生 KafkaConsumer，零 Spring 依赖）
 *
 * 消费规则：
 *   - Topic 命名规范：workflow.{bindKey}（对应 wf_definition.bind_key 的 MQ 类型）
 *   - 消息格式：JSON，包含 workflowId（可选）和 params
 *   - 消息 Header：workflow-id（可选）、trace-id（透传）、reply-topic（可选）
 *
 * 执行结果处理：
 *   - 若消息含 reply-topic Header → 将结果发布到 reply-topic
 *   - 否则 → 仅记录日志（fire-and-forget 模式）
 *
 * 错误处理：
 *   - WorkflowException → 记录 warn 日志，发送到 DLT（Dead Letter Topic），手动 commit
 *   - 系统异常 → 记录 error 日志，不 commit（触发 Kafka 重试）
 *
 * 生命周期：
 *   - [start]：订阅 topic pattern，启动消费线程
 *   - [stop]：wakeup consumer，关闭线程池和 consumer
 *
 * @param consumer 原生 KafkaConsumer（由调用方创建并配置）
 * @param publisher MQ 发布器，用于发送 reply-topic 和 DLT 消息
 * @param workflowRouter 工作流路由器
 * @param topicPattern 订阅的 topic 正则（默认 `workflow\..*`）
 * @param pollTimeout 单次 poll 超时时间（默认 100ms）
 */
class KafkaWorkflowConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val publisher: MqPublisher,
    private val workflowRouter: WorkflowRouter,
    private val topicPattern: String = "workflow\\..*",
    private val pollTimeout: Duration = Duration.ofMillis(100)
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    /** 消费循环执行器（虚拟线程，Java 21+） */
    private val executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("kafka-workflow-consumer", 0).factory()
    )

    /**
     * 启动消费者
     */
    fun start() {
        if (running.compareAndSet(false, true)) {
            consumer.subscribe(Pattern.compile(topicPattern))
            executor.submit(::consumeLoop)
            log.info { "KafkaWorkflowConsumer started, topicPattern=[$topicPattern]" }
        }
    }

    /**
     * 停止消费者（优雅关闭）
     */
    fun stop() {
        if (running.compareAndSet(true, false)) {
            consumer.wakeup()
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
            consumer.close()
            log.info { "KafkaWorkflowConsumer stopped" }
        }
    }

    /**
     * 消费主循环（在虚拟线程上运行）。
     *
     * 不断 poll Kafka 记录并逐条处理，
     * 收到 WakeupException 时检查 running 标志决定是否退出。
     */
    private fun consumeLoop() {
        try {
            while (running.get()) {
                val records = try {
                    consumer.poll(pollTimeout)
                } catch (ex: WakeupException) {
                    if (!running.get()) break else throw ex
                }
                for (record in records) {
                    process(record)
                }
            }
        } catch (ex: Exception) {
            log.error(ex) { "KafkaWorkflowConsumer loop failed: ${ex.message}" }
        }
    }

    /**
     * 处理单条 Kafka 消息。
     *
     * 流程：提取 headers → 解析 payload → 构建 UnifiedRequest → 调用路由器
     *       → 若有 reply-topic 则发布结果 → commitSync
     *
     * 错误处理：
     *   - WorkflowException → 发 DLT + commitSync（不重试）
     *   - 系统异常 → 不 commit（让 Kafka 重试）
     */
    private fun process(record: ConsumerRecord<String, String>) {
        val topic = record.topic()
        log.debug { "Kafka workflow event on topic [$topic], key [${record.key()}]" }

        try {
            val headers = buildHeaders(record)
            val payload = parsePayload(record.value())
            val workflowId = headers["workflow-id"] ?: payload["workflowId"] as? String

            val unified = if (workflowId != null) {
                UnifiedRequest.withSchemaFromHeaders("INTERNAL", workflowId, headers, payload)
            } else {
                UnifiedRequest.withSchemaFromHeaders("KAFKA:$topic", null, headers, payload, record.value())
            }

            val result = workflowRouter.execute(unified)

            // 若有 reply-topic，将执行结果发布到指定 topic
            val replyTopic = headers["reply-topic"]
            if (!replyTopic.isNullOrBlank()) {
                val replyPayload = JsonUtil.serialize(
                    mapOf("success" to true, "data" to result.data, "traceId" to result.executionId)
                )
                publisher.publish(replyTopic, record.key(), replyPayload, async = true)
                log.debug { "Workflow result sent to reply-topic [$replyTopic]" }
            }

            consumer.commitSync()

        } catch (ex: WorkflowException) {
            log.warn { "Kafka workflow error [${ex.errorCode}] on topic [$topic]: ${ex.message}" }
            sendToDeadLetterTopic(topic, record, ex.errorCode, ex.message ?: "")
            consumer.commitSync()

        } catch (ex: Exception) {
            log.error(ex) { "Kafka unexpected error on topic [$topic]: ${ex.message}" }
            // 不 commit：让 Kafka 根据 auto.offset.reset 策略重试
        }
    }

    /**
     * 发送到 Dead Letter Topic（DLT）
     * DLT Topic 命名：{originalTopic}.DLT
     */
    private fun sendToDeadLetterTopic(
        originalTopic: String,
        record: ConsumerRecord<String, String>,
        errorCode: String,
        errorMessage: String
    ) {
        try {
            val dltTopic = "$originalTopic.DLT"
            val dltPayload = JsonUtil.serialize(
                mapOf(
                    "originalTopic" to originalTopic,
                    "originalKey" to record.key(),
                    "originalOffset" to record.offset(),
                    "originalValue" to record.value(),
                    "errorCode" to errorCode,
                    "errorMessage" to errorMessage,
                    "timestamp" to System.currentTimeMillis()
                )
            )
            publisher.publish(dltTopic, record.key(), dltPayload, async = true)
        } catch (ex: Exception) {
            log.error(ex) { "Failed to send to DLT: ${ex.message}" }
        }
    }

    /** 解析 JSON 消息体，解析失败时降级为 {rawMessage: value}。 */
    private fun parsePayload(value: String): Map<String, Any> {
        return try {
            JsonUtil.toMap(value)
        } catch (_: Exception) {
            mapOf("rawMessage" to value)
        }
    }

    /** 将 Kafka 消息 headers 提取为 String-String Map（二进制值转 String）。 */
    private fun buildHeaders(record: ConsumerRecord<String, String>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        record.headers().forEach { header ->
            headers[header.key()] = String(header.value())
        }
        return headers
    }
}
