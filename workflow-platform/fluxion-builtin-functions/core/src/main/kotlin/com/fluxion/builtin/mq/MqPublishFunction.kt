package com.fluxion.builtin.mq

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas

import com.fluxion.adapter.spi.mq.MqPublisher
import com.fluxion.builtin.json.JsonSerializeFunction
import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.SideEffect
import org.slf4j.*

/**
 * 内置 MQ 消息发布函数（builtin:mqPublish）
 *
 * 通过 [MqPublisher] SPI 屏蔽底层 MQ 实现细节，支持 Kafka、RabbitMQ、RocketMQ 等。
 * 实际 [MqPublisher] 实现由对应适配器模块（如 workflow-adapter-spring-kafka）提供。
 */
class MqPublishFunction(
    private val mqPublisher: MqPublisher
) : WorkflowFunction<Map<String, Any?>>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Map<String, Any?>> {
        val topic = input.requireParam<String>("topic")
        val key = input.param("key", "")
        val async = input.paramAsBoolean("async", false)

        // value 参数优先，未配置时 fallback 到 directInput（上游节点输出）
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
            throw WorkflowNodeException(meta().name, ex)
        }

        if (!result.success) {
            throw WorkflowNodeException(
                meta().name,
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

    override fun meta() = BuiltinFunctionMetas.MQ_PUBLISH
}
