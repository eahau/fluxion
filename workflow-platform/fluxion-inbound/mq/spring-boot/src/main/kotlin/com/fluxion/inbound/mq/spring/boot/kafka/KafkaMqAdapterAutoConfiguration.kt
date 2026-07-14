package com.fluxion.inbound.mq.spring.boot.kafka

import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.inbound.mq.kafka.KafkaConsumerTriggerMeta
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot auto-configuration for the Kafka inbound adapter — companion to the
 * `@Configuration` classes that assemble producer/consumer factories.
 *
 * This class focuses on the new trigger-function metadata SPI: it registers a single
 * [KafkaConsumerTriggerMeta] descriptor bean that the Admin console imports via
 * `TriggerFunctionMetaRegistry` so the designer can render a fully dynamic form for
 * Kafka-consumer triggers without hard-coded UI panels.
 */
@Configuration
@ConditionalOnClass(KafkaConsumer::class, KafkaConsumerTriggerMeta::class)
@ConditionalOnProperty(name = ["workflow.mq.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaMqAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun kafkaConsumerTriggerMeta(): TriggerFunctionMeta = KafkaConsumerTriggerMeta().also {
        log.info { "Registering KafkaConsumerTriggerMeta (functionRef=${it.functionRef}, params=${it.paramSchema.size})" }
    }
}
