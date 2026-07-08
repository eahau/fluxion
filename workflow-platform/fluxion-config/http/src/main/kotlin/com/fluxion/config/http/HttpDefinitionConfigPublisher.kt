package com.fluxion.config.http

import com.fluxion.adapter.spi.config.DefinitionConfigPublisher
import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.adapter.spi.registry.PublishTarget
import com.fluxion.config.core.HttpPushClient
import com.fluxion.core.util.JsonUtil
import org.slf4j.*
import org.slf4j.info

/**
 * HTTP 榛樿瀹炵幇 鈥?Admin 渚у畾涔夊彂甯冨櫒
 */
class HttpDefinitionConfigPublisher(
    private val instanceDiscovery: InstanceDiscovery
) : DefinitionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PUSH_PATH = "/internal/workflow/definition/push"
    }

    private val pushClient = HttpPushClient(log)

    override fun publish(workflowId: String, definitionJson: String, version: Int) {
        publish(workflowId, definitionJson, version, PublishTarget.all())
    }

    override fun publish(workflowId: String, definitionJson: String, version: Int, target: PublishTarget) {
        val instances = HttpPushClient.resolveTargetInstances(target, instanceDiscovery)
        val payload = HttpDefinitionPushPayload(workflowId, definitionJson, version, false)
        val body = serialize(payload, "push payload for workflow: `$workflowId")

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, workflowId)
        log.info {
            "Published workflow [$workflowId] v$version to ${instances.size} instances " +
                "(success=$success, fail=$fail)"
        }
    }

    override fun unpublish(workflowId: String) {
        val instances = instanceDiscovery.getAllInstances()
        val payload = HttpDefinitionPushPayload(workflowId, null, 0, true)
        val body = serialize(payload, "unpublish payload for: `$workflowId")

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, workflowId)
        log.info {
            "Unpublished workflow [$workflowId] from ${instances.size} instances " +
                "(success=$success, fail=$fail)"
        }
    }

    private fun serialize(value: Any, errorContext: String): String {
        return try {
            JsonUtil.serialize(value)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize $errorContext", e)
        }
    }
}
