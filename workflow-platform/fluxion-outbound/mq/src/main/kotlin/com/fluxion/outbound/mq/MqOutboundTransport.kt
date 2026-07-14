package com.fluxion.outbound.mq

import com.fluxion.outbound.mq.spi.MqPublishResult
import com.fluxion.outbound.mq.spi.MqPublisher
import com.fluxion.outbound.OutboundConfig
import com.fluxion.outbound.OutboundRequest
import com.fluxion.outbound.OutboundResponse
import com.fluxion.outbound.OutboundTransport
import com.fluxion.core.util.JsonUtil
import org.slf4j.LoggerFactory
import org.slf4j.*

class MqOutboundTransport(
    private val publisher: MqPublisher
) : OutboundTransport {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun protocol(): String = "mq"

    override fun isOneWay(): Boolean = true

    override suspend fun invokeSuspend(request: OutboundRequest): OutboundResponse {
        val config = request.config
        val topic = config.service
            ?: return errorResponse("MQ external function config.service (topic) is required")

        val key = config.method ?: ""
        val async = config.extraString("sendAsync").equals("true", ignoreCase = true)

        val body = try {
            when (val input = request.input) {
                null -> "{}"
                is String -> input
                else -> JsonUtil.serialize(input)
            }
        } catch (ex: Exception) {
            log.error(ex) { "MQ external invoke failed to serialize input for topic=$topic" }
            return OutboundResponse(
                success = false,
                output = null,
                errorCode = "MQ_SERIALIZE_ERROR",
                errorMessage = ex.message ?: ex.javaClass.name
            )
        }

        val result: MqPublishResult = try {
            log.debug { "MQ external invoke topic=[$topic] key=[$key] async=[$async]" }
            publisher.publish(topic, key, body, async)
        } catch (ex: Exception) {
            log.error(ex) { "MQ external invoke publish exception topic=[$topic] key=[$key]" }
            MqPublishResult(success = false, topic = topic, error = ex.message ?: ex.javaClass.name)
        }

        return if (result.success) {
            OutboundResponse(
                success = true,
                output = mapOf(
                    "topic" to (result.topic ?: topic),
                    "partition" to result.partition,
                    "offset" to result.offset,
                    "messageId" to result.messageId,
                    "async" to async
                )
            )
        } else {
            OutboundResponse(
                success = false,
                output = null,
                errorCode = "MQ_PUBLISH_FAILED",
                errorMessage = result.error
                    ?: "MqPublisher reported failure for topic=${result.topic ?: topic}"
            )
        }
    }

    private fun errorResponse(message: String): OutboundResponse =
        OutboundResponse(
            success = false,
            output = null,
            errorCode = "MQ_CONFIG_ERROR",
            errorMessage = message
        )
}
