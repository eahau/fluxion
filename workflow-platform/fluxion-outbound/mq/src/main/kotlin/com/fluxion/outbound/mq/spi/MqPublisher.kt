package com.fluxion.outbound.mq.spi

data class MqPublishResult(
    val success: Boolean,
    val topic: String? = null,
    val partition: Int? = null,
    val offset: Long? = null,
    val messageId: String? = null,
    val error: String? = null
)

fun interface MqPublisher {
    fun publish(topic: String, key: String, message: String, async: Boolean): MqPublishResult
}
