package com.fluxion.adapter.mq.spring.boot

import com.fluxion.adapter.mq.kafka.KafkaMqPublisher
import com.fluxion.adapter.mq.kafka.KafkaWorkflowConsumer
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.adapter.spi.mq.MqPublisher
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
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MQ 适配器 Spring Boot 自动装配入口
 *
 * 当前支持 Kafka，未来可在此扩展 RabbitMQ / RocketMQ 等装配逻辑。
 *
 * 触发条件：
 *   - classpath 存在 KafkaProducer 与 KafkaConsumer
 *   - 配置 workflow.adapter.mq.kafka.enabled = true（默认关闭）
 * 提供 Bean：
 *   - KafkaProducer<String, String>
 *   - KafkaConsumer<String, String>
 *   - MqPublisher（KafkaMqPublisher 实现）
 *   - KafkaWorkflowConsumer（由 SmartLifecycle 在容器启动后启动消费线程）
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

    @Bean
    @ConditionalOnMissingBean(MqPublisher::class)
    fun kafkaMqPublisher(producer: KafkaProducer<String, String>): MqPublisher = KafkaMqPublisher(producer)

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

    @Bean
    @ConditionalOnMissingBean
    fun kafkaWorkflowConsumer(
        consumer: KafkaConsumer<String, String>,
        publisher: MqPublisher,
        workflowRouter: WorkflowRouter,
        @Value("\${workflow.adapter.mq.kafka.topic-pattern:workflow\\..*}") topicPattern: String,
        @Value("\${workflow.adapter.mq.kafka.poll-timeout-ms:100}") pollTimeoutMs: Long
    ): KafkaWorkflowConsumer = KafkaWorkflowConsumer(
        consumer,
        publisher,
        workflowRouter,
        topicPattern,
        Duration.ofMillis(pollTimeoutMs)
    )

    /**
     * 管理 KafkaWorkflowConsumer 生命周期：容器启动后开始消费，关闭前停止消费。
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

        override fun getPhase(): Int = Integer.MAX_VALUE - 1000

        override fun destroy() = stop()
    }
}
