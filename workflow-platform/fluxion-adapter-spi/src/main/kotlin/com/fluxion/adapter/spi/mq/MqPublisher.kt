package com.fluxion.adapter.spi.mq

/**
 * MQ 消息发布结果（协议无关）
 *
 * @param success 是否发送成功
 * @param topic 目标 Topic
 * @param partition 分区（Kafka 等分区 MQ 使用）
 * @param offset 偏移量（Kafka 等分区 MQ 使用）
 * @param messageId 消息唯一标识（RocketMQ/RabbitMQ 等使用）
 * @param error 失败原因（成功时为 null）
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
 * MQ 消息发布适配器 SPI（协议无关）
 *
 * 支持不同 MQ 实现：Kafka、RabbitMQ、RocketMQ 等。
 * 实现类只需关注如何把一个字符串消息发送到指定 Topic/Queue/Exchange。
 */
fun interface MqPublisher {

    /**
     * 发布消息到指定 Topic
     *
     * @param topic 目标 Topic
     * @param key 消息 Key（分区路由，可选）
     * @param message 消息内容（已序列化的字符串）
     * @param async 是否异步发送；true 时方法应立即返回，由实现方自行处理回调/异常
     * @return 发布结果元数据
     */
    fun publish(topic: String, key: String, message: String, async: Boolean): MqPublishResult
}
