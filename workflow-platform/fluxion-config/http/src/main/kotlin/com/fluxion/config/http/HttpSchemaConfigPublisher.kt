package com.fluxion.config.http

import com.fluxion.config.core.SchemaConfigPublisher
import com.fluxion.config.core.SchemaConfigSnapshot
import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.PublishTarget
import com.fluxion.config.core.HttpPushClient
import com.fluxion.core.util.JsonUtil
import org.slf4j.LoggerFactory

/**
 * HTTP Schema config publisher — Admin side.
 *
 * Pushes schema updates to all Worker instances via the `/internal/workflow/schema/push` endpoint.
 */
class HttpSchemaConfigPublisher(
    instanceDiscovery: InstanceDiscovery
) : SchemaConfigPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PUSH_PATH = "/internal/workflow/schema/push"
    }

    private val instanceDiscovery = instanceDiscovery
    private val pushClient = HttpPushClient(log)

    override fun publish(snapshot: SchemaConfigSnapshot) {
        publish(snapshot, PublishTarget.all())
    }

    override fun publish(snapshot: SchemaConfigSnapshot, target: PublishTarget) {
        val instances = HttpPushClient.resolveTargetInstances(target, instanceDiscovery)
        val body = serialize(snapshot, "schema snapshot: ${snapshot.schemaName}")

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, snapshot.schemaName)
        log.info(
            "Published schema [${snapshot.schemaName}] to ${instances.size} instances " +
                "(success=$success, fail=$fail)"
        )
    }

    override fun unpublish(schemaName: String) {
        val instances = instanceDiscovery.getAllInstances()
        val body = serialize(
            SchemaConfigSnapshot.removed(schemaName),
            "unpublish payload for schema: $schemaName"
        )

        val (success, fail) = pushClient.push(instances, PUSH_PATH, body, schemaName)
        log.info(
            "Unpublished schema [$schemaName] from ${instances.size} instances " +
                "(success=$success, fail=$fail)"
        )
    }

    private fun serialize(value: Any, errorContext: String): String {
        return try {
            JsonUtil.serialize(value)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize $errorContext", e)
        }
    }
}
