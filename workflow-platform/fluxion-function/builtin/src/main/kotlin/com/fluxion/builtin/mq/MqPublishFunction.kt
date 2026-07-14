package com.fluxion.builtin.mq

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.outbound.mq.spi.MqPublisher
import com.fluxion.builtin.json.JsonSerializeFunction
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.SideEffect
import org.slf4j.*
/**
 * Built-in message-queue publish function (`builtin:mqPublish`).
 *
 * Sends a JSON-serialized message through the pluggable [MqPublisher] SPI —
 * the actual broker client (Kafka, RabbitMQ, RocketMQ, etc.) is supplied by
 * a workflow-adapter module such as `workflow-adapter-spring-kafka`.
 *
 * Node params:
 * - `topic`       — target topic/exchange name (required)
 * - `key`         — partition key for ordered topics (default empty)
 * - `async`       — if true, the publish returns before broker ACK (default false)
 * - `value`       — message payload override; if omitted, `directInput` is used
 *
 * On success attaches a `"MQ_SEND"` [SideEffect] so saga compensations and
 * audit streams can replay the exact topic+key+payload.
 */
class MqPublishFunction(
    private val mqPublisher: MqPublisher
) : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:mqPublish"

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val topic = input.requireParam<String>("topic")
        val key = input.param("key", "")
        val async = input.paramAsBoolean("async", false)

        // Prefer explicit `value` param; fallback to directInput (usual case in
        // pipelines where this function follows a transform node).
        val rawValue = input.param<Any?>("value") ?: input.directInput
        val messageJson = try {
            JsonSerializeFunction.serialize(rawValue)
        } catch (_: Exception) {
            rawValue?.toString() ?: ""
        }

        log.debug { "MqPublishFunction sending to topic [$topic], key [$key], async=[$async]" }

        val result = try {
            mqPublisher.publish(topic, key, messageJson, async)
        } catch (ex: Exception) {
            throw WorkflowNodeException(functionName, ex)
        }

        if (!result.success) {
            throw WorkflowNodeException(
                functionName,
                RuntimeException("MQ publish failed: ${result.error}")
            )
        }

        val resultMeta = buildMap {
            put("topic", result.topic ?: topic)
            put("mode", if (async) "async" else "sync")
            result.partition?.let { put("partition", it) }
            result.offset?.let { put("offset", it) }
            result.messageId?.let { put("messageId", it) }
        }

        val effects = listOf(SideEffect(
            "MQ_SEND", topic,
            mapOf("key" to key, "messageJson" to messageJson),
            null
        ))

        return FunctionResult.successWithEffects(resultMeta, effects)
    }
}
