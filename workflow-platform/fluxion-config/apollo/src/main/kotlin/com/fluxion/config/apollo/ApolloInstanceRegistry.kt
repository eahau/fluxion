package com.fluxion.config.apollo

import com.ctrip.framework.apollo.openapi.client.ApolloOpenApiClient
import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry
import com.fluxion.core.util.JsonUtil
import org.slf4j.*

/**
 * Apollo implementation - instance registry (Worker side).
 */
class ApolloInstanceRegistry(
    private val openApiClient: ApolloOpenApiClient,
    private val appId: String,
    private val env: String,
    private val clusterName: String,
    private val operator: String
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INSTANCES_NAMESPACE = "workflow-instances"
    }

    override fun register(instance: InstanceInfo) {
        try {
            publishItemToApollo(
                client       = openApiClient,
                appId        = appId,
                env          = env,
                cluster      = clusterName,
                namespace    = INSTANCES_NAMESPACE,
                key          = instance.instanceId,
                value        = JsonUtil.serialize(instance),
                comment      = "appGroup=${instance.appGroup}",
                releaseTitle = "Register instance: ${instance.instanceId}",
                operator     = operator
            )
            log.info { "Registered instance to Apollo: instanceId=${instance.instanceId} appGroup=${instance.appGroup}" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to register instance to Apollo: ${instance.instanceId}", e)
        }
    }

    override fun deregister(instanceId: String) {
        try {
            unpublishItemFromApollo(
                client       = openApiClient,
                appId        = appId,
                env          = env,
                cluster      = clusterName,
                namespace    = INSTANCES_NAMESPACE,
                key          = instanceId,
                releaseTitle = "Deregister instance: $instanceId",
                operator     = operator
            )
            log.info { "Deregistered instance from Apollo: instanceId=$instanceId" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to deregister instance from Apollo: $instanceId (may not exist)" }
        }
    }

    override fun heartbeat(instanceId: String) {
        try {
            val item = buildItem(
                key      = instanceId,
                value    = System.currentTimeMillis().toString(),
                comment  = "heartbeat",
                operator = operator
            )
            openApiClient.createOrUpdateItem(appId, env, clusterName, INSTANCES_NAMESPACE, item)
            log.debug { "Heartbeat sent for instanceId=$instanceId" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to send heartbeat for instanceId=$instanceId" }
        }
    }
}
