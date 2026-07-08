package com.fluxion.adapter.spi.mq

/**
 * Message Queue publishing SPI — protocol-agnostic abstraction for sending
 * string messages to a topic/queue.
 *
 * Supports multiple MQ implementations (Kafka, RabbitMQ, RocketMQ, etc.).
 * Implementations only need to focus on how to deliver a serialized string
 * payload to a named destination.
 */

/**
 * Result wrapper for an MQ publish operation.
 *
 * Populated fields vary by MQ implementation:
 * - Kafka: topic, partition, offset are always populated on success
 * - RocketMQ/RabbitMQ: messageId is the primary identifier
 *
 * @param success   Whether the message was successfully sent
 * @param topic     Target topic/queue name (optional, always populated for Kafka)
 * @param partition Partition number (partitioned MQs only)
 * @param offset    Message offset within the partition (Kafka only)
 * @param messageId Message unique identifier (RocketMQ/RabbitMQ-style)
 * @param error     Error detail on failure; null on success
 */
data class MqPublishResult(
    val success: Boolean,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val messageId: String? = null,
    val error: String? = null
)

/**
 * MQ publisher SPI — implementations provide send capability to a specific
 * message broker.
 *
 * Callers (e.g. the Dead Letter Queue handler in [KafkaWorkflowConsumer])
 * use this interface without depending on Kafka/RabbitMQ client libraries
 * directly.
 */
fun interface MqPublisher {

    /**
     * Publish a string message to the specified topic.
     *
     * @param topic   Target topic/queue/exchange name
     * @param key     Message key for partition routing (optional; pass empty string if not used)
     * @param message Serialized string payload (usually JSON)
     * @param async   If true, return immediately without waiting for broker ACK.
     *                The implementation handles callbacks and error logging internally.
     *                If false, block until the broker confirms delivery.
     * @return Publish result metadata (partial for async mode)
     */
    fun publish(topic: String, key: String, message: String, async: Boolean): MqPublishResult
}
