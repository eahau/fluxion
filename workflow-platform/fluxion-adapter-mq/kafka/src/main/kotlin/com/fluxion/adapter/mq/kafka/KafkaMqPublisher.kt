package com.fluxion.adapter.mq.kafka

import com.fluxion.adapter.spi.mq.MqPublishResult
import com.fluxion.adapter.spi.mq.MqPublisher
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.*
import java.util.concurrent.TimeUnit

/**
 * Kafka 实现的 [MqPublisher]
 *
 * 基于原生 KafkaProducer，零 Spring 依赖。
 * 同步发送：阻塞等待 broker 确认，返回 topic/partition/offset
 * 异步发送：立即返回，通过回调记录发送结果
 *
 * @param producer 原生 KafkaProducer（由调用方管理生命周期）
 */
class KafkaMqPublisher(
    private val producer: KafkaProducer<String, String>
) : MqPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

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
        private const val SYNC_TIMEOUT_SECONDS = 5L
    }
}
