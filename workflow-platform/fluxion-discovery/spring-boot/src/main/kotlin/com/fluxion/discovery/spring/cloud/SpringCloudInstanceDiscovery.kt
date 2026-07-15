package com.fluxion.discovery.spring.cloud

import com.fluxion.discovery.core.InstanceDiscovery
import com.fluxion.registry.core.InstanceInfo
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.ServiceInstance

class SpringCloudInstanceDiscovery(
    private val discoveryClient: DiscoveryClient
) : InstanceDiscovery {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SERVICE_PREFIX = "workflow-"
    }

    override fun getAllInstances(): List<InstanceInfo> = try {
        discoveryClient.services
            .filter { it.startsWith(SERVICE_PREFIX) }
            .flatMap { serviceId ->
                discoveryClient.getInstances(serviceId)
                    .asSequence()
                    .map { toInstanceInfo(it, serviceId) }
            }
    } catch (e: Exception) {
        log.error(e) { "Failed to get all instances from Spring Cloud Discovery" }
        emptyList()
    }

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> {
        val serviceId = SERVICE_PREFIX + appGroup
        return try {
            discoveryClient.getInstances(serviceId)
                .asSequence()
                .map { toInstanceInfo(it, serviceId) }
                .toList()
        } catch (e: Exception) {
            log.error(e) { "Failed to get instances from Spring Cloud Discovery for group=$appGroup" }
            emptyList()
        }
    }

    override fun getAllGroups(): List<String> = try {
        discoveryClient.services
            .filter { it.startsWith(SERVICE_PREFIX) }
            .map { it.removePrefix(SERVICE_PREFIX) }
    } catch (e: Exception) {
        log.error(e) { "Failed to get all groups from Spring Cloud Discovery" }
        emptyList()
    }

    private fun toInstanceInfo(instance: ServiceInstance, serviceId: String): InstanceInfo {
        val metadata = instance.metadata
        return InstanceInfo(
            instanceId = metadata.getOrDefault("instanceId", "${instance.host}:${instance.port}"),
            appGroup = metadata.getOrDefault("appGroup", serviceId.removePrefix(SERVICE_PREFIX)),
            host = instance.host,
            port = instance.port,
            metadata = metadata,
            lastHeartbeatMs = System.currentTimeMillis()
        )
    }
}