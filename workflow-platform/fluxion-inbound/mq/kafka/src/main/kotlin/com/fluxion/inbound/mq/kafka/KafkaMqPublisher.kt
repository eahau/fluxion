package com.fluxion.inbound.mq.kafka

import com.fluxion.outbound.mq.spi.MqPublishResult
import com.fluxion.outbound.mq.spi.MqPublisher
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.*
import java.util.concurrent.TimeUnit

/**
 * Kafka-backed implementation of the transport-agnostic [MqPublisher] SPI.
 *
 * Operates directly on the native `KafkaProducer<String,String>` (no Spring
 * wrapper) to keep the dependency surface minimal — callers can configure
 * the producer any way they wish (SSL, compression, batching, idempotence …)
 * before passing it in. Caller also retains ownership of the producer
 * lifecycle (flush/close) except in the Spring Boot auto-config path where
 * SmartLifecycle closes it on shutdown.
 *
 * Two publish modes are provided via the `async` flag:
 * - **Synchronous** – blocks the calling thread until broker ACK arrives
 *   (`acks=all` honouring min-insync-replicas). Used for fire-and-*verify*
 *   operations (e.g. synchronous reply-topic publish within the consumer).
 * - **Asynchronous** – queues the record and returns immediately. Broker
 *   result is captured via the `onCompletion` callback — success is
 *   debug-logged, failures are error-logged (no retry; retry on produce
 *   should be done by the producer's own `retries` config).
 *
 * @param producer Caller-provided, lifecycle-managed Kafka producer.
 */
class KafkaMqPublisher(
    private val producer: KafkaProducer<String, String>
) : MqPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Publish a single String record.
     *
     * @param topic  Target Kafka topic (no auto-creation; topic is expected to exist).
     * @param key    Record key for partition assignment; empty string → round-robin.
     * @param message UTF-8 message body (caller serialises; we never re-encode).
     * @param async  If true queue-and-return; if false wait for broker ack.
     * @return Result capturing success/failure + broker-assigned metadata.
     */
    override fun publish(topic: String, key: String, message: String, async: Boolean): MqPublishResult {
        log.debug { "KafkaMqPublisher send to topic [$topic], key [$key], async=[$async]" }

        return try {
            if (async) {
                publishAsync(topic, key, message)
            } else {
                publishSync(topic, key, message)
            }
        } catch (ex: Exception) {
            log.error(ex) { "KafkaMqPublisher failed to send topic [$topic], key [$key]" }
            MqPublishResult(
                success = false,
                topic = topic,
                error = ex.message ?: ex.javaClass.simpleName
            )
        }
    }

    /**
     * Synchronous publish. Blocks up to [SYNC_TIMEOUT_SECONDS] for the broker
     * future to resolve — longer than that likely means the producer's
     * `delivery.timeout.ms` has already kicked in and the future is failed.
     */
    private fun publishSync(topic: String, key: String, message: String): MqPublishResult {
        val metadata: RecordMetadata = producer.send(ProducerRecord(topic, key, message))
            .get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        log.debug {
            "KafkaMqPublisher sync success topic [${metadata.topic()}], partition [${metadata.partition()}], offset [${metadata.offset()}]"
        }

        return MqPublishResult(
            success = true,
            topic = metadata.topic(),
            partition = metadata.partition(),
            offset = metadata.offset()
        )
    }

    /**
     * Asynchronous publish. Future's onCompletion callback records metadata
     * OR the produce exception — note: produce exceptions are NOT surfaced
     * through the return value (design choice: async callers poll via the
     * broker UI / metrics rather than a synchronous result object).
     */
    private fun publishAsync(topic: String, key: String, message: String): MqPublishResult {
        producer.send(ProducerRecord(topic, key, message)) { metadata, exception ->
            if (exception != null) {
                log.error(exception) { "KafkaMqPublisher async failed topic [$topic], key [$key]" }
            } else {
                log.debug {
                    "KafkaMqPublisher async success topic [${metadata?.topic()}], partition [${metadata?.partition()}], offset [${metadata?.offset()}]"
                }
            }
        }

        return MqPublishResult(success = true, topic = topic)
    }

    companion object {
        /** Max wall-clock wait for a synchronous broker ACK before we give up. */
        private const val SYNC_TIMEOUT_SECONDS = 5L
    }
}
