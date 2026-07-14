package com.fluxion.inbound.mq.spring.boot

import com.fluxion.inbound.mq.kafka.KafkaMqPublisher
import com.fluxion.inbound.mq.kafka.KafkaWorkflowConsumer
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.outbound.mq.spi.MqPublisher
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.*
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Spring Boot auto-configuration for the MQ (Kafka) transport adapter.
 *
 * Gate conditions:
 * - `KafkaProducer` + `KafkaConsumer` are on the runtime classpath (i.e. the
 *   app includes the native kafka-clients jar — NOT spring-kafka).
 * - Property `workflow.adapter.mq.kafka.enabled=true` (defaults OFF so
 *   HTTP-only deployments don't pull in the consumer thread + producer).
 *
 * Beans provided:
 * 1. `KafkaProducer<String,String>` — with `acks=all`, `retries=3` for strong durability.
 * 2. `KafkaConsumer<String,String>` — manual commit (auto-commit=false),
 *    earliest-offset reset so new consumer groups replay from the start.
 * 3. `MqPublisher` (KafkaMqPublisher impl) — used by both the workflow
 *    consumer (reply-topic + DLT publishes) and user workflows.
 * 4. `KafkaWorkflowConsumer` — the core subscriber/executor component.
 * 5. `SmartLifecycle` wrapper — binds the consumer lifecycle to the Spring
 *    context: start() on context refreshed, stop() on context close. Also
 *    closes the Kafka producer on stop.
 *
 * Extensibility: replacing RabbitMQ or RocketMQ in a future release is a
 * matter of writing a parallel auto-config with `@ConditionalOnClass` for
 * those client classes and exposing `MqPublisher` + the consumer.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaProducer::class, KafkaConsumer::class)
@ConditionalOnProperty(
    prefix = "workflow.adapter.mq.kafka",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MqAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Build the shared Kafka producer.
     *
     * Producer is intentionally configured conservatively:
     * - `acks=all` — require all in-sync replicas before ack (safe defaults for workflow replies).
     * - `retries=3` — retry transient produce failures before surfacing to caller.
     */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaProducer(
        @Value("\${workflow.adapter.mq.kafka.bootstrap-servers:localhost:9092}") bootstrapServers: String
    ): KafkaProducer<String, String> {
        val props = Properties()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.ACKS_CONFIG] = "all"
        props[ProducerConfig.RETRIES_CONFIG] = 3
        return KafkaProducer(props)
    }

    /** Wrap the producer as the transport-agnostic MqPublisher SPI. */
    @Bean
    @ConditionalOnMissingBean(MqPublisher::class)
    fun kafkaMqPublisher(producer: KafkaProducer<String, String>): MqPublisher = KafkaMqPublisher(producer)

    /**
     * Build the shared Kafka consumer.
     *
     * Manual commit is REQUIRED (`enable.auto.commit=false`) because the
     * consumer's commit boundary is per-record and conditional (we only
     * commit after successful processing OR after DLT routing — never after
     * infrastructure errors that should be retried).
     */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaConsumer(
        @Value("\${workflow.adapter.mq.kafka.bootstrap-servers:localhost:9092}") bootstrapServers: String,
        @Value("\${workflow.adapter.mq.kafka.group-id:workflow-engine}") groupId: String,
        @Value("\${workflow.adapter.mq.kafka.auto-offset-reset:earliest}") autoOffsetReset: String,
        @Value("\${workflow.adapter.mq.kafka.enable-auto-commit:false}") enableAutoCommit: Boolean
    ): KafkaConsumer<String, String> {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = autoOffsetReset
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = enableAutoCommit.toString()
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        return KafkaConsumer(props)
    }

    /** Build the workflow consumer (stateless; delegates to InboundRouter). */
    @Bean
    @ConditionalOnMissingBean
    fun kafkaWorkflowConsumer(
        consumer: KafkaConsumer<String, String>,
        publisher: MqPublisher,
        inboundRouter: InboundRouter,
        @Value("\${workflow.adapter.mq.kafka.topic-pattern:workflow\\..*}") topicPattern: String,
        @Value("\${workflow.adapter.mq.kafka.poll-timeout-ms:100}") pollTimeoutMs: Long
    ): KafkaWorkflowConsumer = KafkaWorkflowConsumer(
        consumer,
        publisher,
        inboundRouter,
        topicPattern,
        Duration.ofMillis(pollTimeoutMs)
    )

    /**
     * Bind [KafkaWorkflowConsumer] lifecycle to Spring via `SmartLifecycle`.
     *
     * Why not `@EventListener(ContextRefreshedEvent)` + `@PreDestroy`?
     * SmartLifecycle gives us deterministic ordering via `phase` — we run
     * VERY late (phase = Int.MAX_VALUE - 1000) so InboundRouter + all
     * function registrations are guaranteed ready before we start
     * consuming from Kafka. On shutdown we run early enough to drain
     * in-flight polls before the rest of the context is torn down.
     *
     * Also implements [DisposableBean] as safety net for edge-case manual
     * context destruction paths.
     */
    @Bean
    fun kafkaConsumerLifecycle(
        producer: KafkaProducer<String, String>,
        consumer: KafkaWorkflowConsumer
    ): SmartLifecycle = object : SmartLifecycle, DisposableBean {
        private val running = AtomicBoolean(false)

        override fun start() {
            if (!running.getAndSet(true)) {
                consumer.start()
                log.info { "KafkaWorkflowConsumer lifecycle started" }
            }
        }

        override fun stop() {
            if (running.getAndSet(false)) {
                consumer.stop()
                producer.close()
                log.info { "KafkaWorkflowConsumer lifecycle stopped" }
            }
        }

        override fun isRunning(): Boolean = running.get()

        // Phase position: very late start / very early stop so consumer is
        // last-up-first-down relative to the function/runtime beans.
        override fun getPhase(): Int = Integer.MAX_VALUE - 1000

        override fun destroy() = stop()
    }
}
