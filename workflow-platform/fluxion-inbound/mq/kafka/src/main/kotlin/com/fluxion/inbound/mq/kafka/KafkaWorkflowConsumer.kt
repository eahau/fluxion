package com.fluxion.inbound.mq.kafka

import com.fluxion.inbound.spi.UnifiedRequest
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.outbound.mq.spi.MqPublisher
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
 * Kafka event consumer that dispatches inbound messages to the workflow engine.
 *
 * Zero Spring dependency — built directly on the native KafkaConsumer so it can
 * run embedded in non-Spring deployments. Thread-per-task (Java 21+ virtual
 * threads) is used for the single poll-loop thread.
 *
 * **Topic convention:** subscribes via regex to `workflow.<bindKey>` topics,
 * where `<bindKey>` is the `wf_definition.bind_key` value set for MQ-type
 * workflows by the operator console.
 *
 * **Message envelope (JSON body):**
 * ```
 * { "workflowId": "optional", "params": {...} }
 * ```
 *
 * **Kafka Headers we inspect:**
 * - `workflow-id`  – workflow override (takes precedence over body)
 * - `trace-id`     – propagated to routing/execution layer
 * - `reply-topic`  – if present, execution result is published back here
 *
 * **Error handling contract (deliberate two-tier strategy):**
 * - `WorkflowException` → logical error. Route to Dead-Letter Topic (`.DLT`),
 *   then commit offset. We do NOT want infinite retries of a bad payload.
 * - Any other exception → infrastructure error. Leave offset UN-committed so
 *   Kafka's `auto.offset.reset` policy can retry after consumer restart.
 *
 * **Lifecycle:**
 * - [start] – subscribe topic pattern, start virtual consumer thread.
 * - [stop]  – `wakeup()` the consumer (clean way to break a blocked poll),
 *   shut down executor, close the KafkaConsumer.
 */
class KafkaWorkflowConsumer(
    private val consumer: KafkaConsumer<String, String>,
    private val publisher: MqPublisher,
    private val inboundRouter: InboundRouter,
    private val topicPattern: String = "workflow\\..*",
    private val pollTimeout: Duration = Duration.ofMillis(100)
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    /** Single-thread virtual-thread executor for the poll loop. */
    private val executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("kafka-workflow-consumer", 0).factory()
    )

    /**
     * Start the consumer. Idempotent — subsequent calls no-op if already running.
     */
    fun start() {
        if (running.compareAndSet(false, true)) {
            consumer.subscribe(Pattern.compile(topicPattern))
            executor.submit(::consumeLoop)
            log.info { "KafkaWorkflowConsumer started, topicPattern=[$topicPattern]" }
        }
    }

    /**
     * Gracefully shut the consumer down.
     *
     * Uses `KafkaConsumer.wakeup()` (thread-safe call) to interrupt a blocking
     * `poll()` rather than interrupting the thread, which would abort any
     * in-flight commit.
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
     * Main poll loop — runs on a dedicated virtual thread.
     *
     * WakeupException handling: a wake-up during poll ONLY means "exit cleanly
     * please" if `running` has been flipped to false. Otherwise it is a spurious
     * wake-up and we continue looping.
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
     * Process a single Kafka record.
     *
     * Pipeline:
     * 1. Extract headers (binary → String map)
     * 2. Parse JSON payload (fallback to `{rawMessage: value}` on parse error)
     * 3. Determine workflowId: header > body > null (then topic-based resolution)
     * 4. Build [UnifiedRequest] and delegate to [InboundRouter.execute]
     * 5. If `reply-topic` header was present → publish result JSON
     * 6. commitSync (unless we're in the system-exception path)
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

            val result = inboundRouter.execute(unified)

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
            // Logical error — poison the message to DLT, commit the original offset
            log.warn { "Kafka workflow error [${ex.errorCode}] on topic [$topic]: ${ex.message}" }
            sendToDeadLetterTopic(topic, record, ex.errorCode, ex.message ?: "")
            consumer.commitSync()

        } catch (ex: Exception) {
            // Infrastructure error — do NOT commit; rely on Kafka to re-deliver
            log.error(ex) { "Kafka unexpected error on topic [$topic]: ${ex.message}" }
        }
    }

    /**
     * Publish a failed record to the Dead-Letter Topic.
     *
     * DLT topic naming: `{originalTopic}.DLT`
     *
     * The original record is wrapped in an envelope preserving the source
     * topic/key/offset/value plus the failure code/message/timestamp so an
     * operator replay tool has enough context to re-inject the message
     * after a fix is deployed.
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

    /**
     * Parse JSON message body. On parse failure returns a single-key map so
     * downstream `inboundRouter` still has access to the raw bytes for
     * debug/DLT analysis rather than NPE-ing on a null params map.
     */
    private fun parsePayload(value: String): Map<String, Any> {
        return try {
            JsonUtil.toMap(value)
        } catch (_: Exception) {
            mapOf("rawMessage" to value)
        }
    }

    /**
     * Flatten Kafka headers (binary multi-map) into a String→String map.
     * Duplicate keys are last-write-wins, which mirrors the common MQ
     * convention of single-valued headers.
     */
    private fun buildHeaders(record: ConsumerRecord<String, String>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        record.headers().forEach { header ->
            headers[header.key()] = String(header.value())
        }
        return headers
    }
}
