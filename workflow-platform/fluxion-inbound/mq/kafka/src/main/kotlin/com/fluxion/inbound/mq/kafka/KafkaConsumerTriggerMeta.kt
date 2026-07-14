package com.fluxion.inbound.mq.kafka

import com.fluxion.core.model.TriggerType
import com.fluxion.core.spi.LegacyBinding
import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.core.spi.TriggerFunctionParam

class KafkaConsumerTriggerMeta : TriggerFunctionMeta {

    override val functionRef = "trigger:kafkaConsumer"
    override val label = "Kafka Event Consumer"
    override val icon = "MessageOutlined"
    override val description =
        "Subscribe to specified Kafka Topic, each new record triggers a workflow execution. Consumer threads / Offset strategy configured below."

    override val paramSchema: List<TriggerFunctionParam> = listOf(
        TriggerFunctionParam(
            name = "topic",
            type = "string",
            required = true,
            label = "Topic",
            description = "Supports single Topic name (e.g. order.created) or Java regex (e.g. order\\..*)."
        ),
        TriggerFunctionParam(
            name = "consumerGroup",
            type = "string",
            required = true,
            label = "Consumer Group",
            description = "Defaults to appKey auto-prefix; use different groupId for different workflows to avoid offset conflicts.",
            defaultValue = "\${app.key}-default-consumer"
        ),
        TriggerFunctionParam(
            name = "concurrency",
            type = "integer",
            label = "Consumer Threads",
            description = "Concurrent fetch threads per Worker instance; Topic partitions >= this value for full utilization.",
            defaultValue = 1
        ),
        TriggerFunctionParam(
            name = "offsetReset",
            type = "enum",
            label = "Offset Reset Strategy",
            defaultValue = "latest",
            options = listOf(
                "latest" to "Latest (new messages only)",
                "earliest" to "Earliest (replay history)",
                "none" to "Fail if no offset (production only)"
            )
        ),
        TriggerFunctionParam(
            name = "pollTimeoutMs",
            type = "integer",
            label = "Poll Timeout(ms)",
            defaultValue = 100
        ),
        TriggerFunctionParam(
            name = "enableDLT",
            type = "boolean",
            label = "Enable Dead Letter Queue",
            description = "Workflow failures N times will auto-deliver to topic.DLT, preventing consumer block.",
            defaultValue = true
        ),
        TriggerFunctionParam(
            name = "retryAttempts",
            type = "integer",
            label = "Max Retry Attempts",
            description = "Inbound-level retries (not entering workflow), beyond which goes to DLT. 0=no retry.",
            defaultValue = 3
        ),
        TriggerFunctionParam(
            name = "bootstrapServers",
            type = "string",
            label = "Custom Kafka Cluster(optional)",
            description = "Leave empty = use App-bound default KAFKA resource; fill to override connection string.",
            defaultValue = ""
        ),
        TriggerFunctionParam(
            name = "keyDeserializer",
            type = "string",
            label = "Key Deserializer",
            defaultValue = "org.apache.kafka.common.serialization.StringDeserializer"
        ),
        TriggerFunctionParam(
            name = "valueDeserializer",
            type = "string",
            label = "Value Deserializer",
            defaultValue = "org.apache.kafka.common.serialization.StringDeserializer"
        )
    )

    override fun extractLegacyBinding(config: Map<String, Any?>): LegacyBinding? {
        val topic = config["topic"]?.toString() ?: return null
        return LegacyBinding(
            protocol = "KAFKA",
            method = null,
            bindKey = topic,
            triggerType = TriggerType.EVENT
        )
    }

    override fun configFromLegacy(protocol: String, method: String?, bindKey: String?): Map<String, Any?> =
        mapOfNotNull(
            "topic" to bindKey?.takeIf { it.isNotBlank() },
            "consumerGroup" to "\${app.key}-default-consumer"
        )

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
        pairs.filter { it.second != null }.toMap()
}