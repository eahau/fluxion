package com.fluxion.config.http

import com.fluxion.core.util.JsonUtil
import com.fluxion.config.core.FunctionConfigPublisher
import com.fluxion.config.core.FunctionConfigSnapshot
import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.PublishTarget
import com.fluxion.config.core.HttpPushClient
import org.slf4j.*

/**
 * HTTP default implementation - Admin-side function config publisher.
 */
class HttpFunctionConfigPublisher(
    instanceDiscovery: InstanceDiscovery
) : FunctionConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PUSH_PATH = "/internal/workflow/function/push"
    }

    private val instanceDiscovery = instanceDiscovery
    private val pushClient = HttpPushClient(log)

    override fun publish(snapshot: FunctionConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: FunctionConfigSnapshot, target: PublishTarget) {
        val instances = HttpPushClient.resolveTargetInstances(target, instanceDiscovery)
        val body = serialize(snapshot, "function snapshot: ${snapshot.functionName}")

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, snapshot.functionName)
        log.info {
            "Published function [${snapshot.functionName}] to ${instances.size} instances " +
                "(success=$success, fail=$fail)"
        }
    }

    override fun unpublish(functionName: String) {
        val instances = instanceDiscovery.getAllInstances()
        val body = serialize(FunctionConfigSnapshot.removed(functionName), "unpublish payload for function: `$functionName")

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, functionName)
        log.info {
            "Unpublished function [$functionName] from ${instances.size} instances " +
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
