package com.fluxion.config.nacos

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.pojo.Instance
import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.InstanceInfo
import org.slf4j.*

/**
 * Nacos implementation - instance discovery (Admin side).
 */
class NacosInstanceDiscovery(
    private val namingService: NamingService
) : InstanceDiscovery {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GROUP_NAME = "WORKFLOW"
        private const val SERVICE_PREFIX = "workflow-"
    }

    override fun getAllInstances(): List<InstanceInfo> = try {
        namingService.subscribeServices
            .filter { it.name.startsWith(SERVICE_PREFIX) }
            .flatMap { service ->
                namingService.getAllInstances(service.name, GROUP_NAME)
                    .asSequence()
                    .filter { it.isHealthy && it.isEnabled }
                    .map { fromNacosInstance(it, service.name) }
            }
    } catch (e: Exception) {
        log.error(e) { "Failed to get all instances from Nacos" }
        emptyList()
    }

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> {
        val serviceName = SERVICE_PREFIX + appGroup
        return try {
            namingService.getAllInstances(serviceName, GROUP_NAME)
                .asSequence()
                .filter { it.isHealthy && it.isEnabled }
                .map { fromNacosInstance(it, serviceName) }
                .toList()
        } catch (e: Exception) {
            log.error(e) { "Failed to get instances from Nacos for group=$appGroup" }
            emptyList()
        }
    }

    override fun getAllGroups(): List<String> = try {
        namingService.subscribeServices
            .map { it.name }
            .filter { it.startsWith(SERVICE_PREFIX) }
            .map { it.removePrefix(SERVICE_PREFIX) }
    } catch (e: Exception) {
        log.error(e) { "Failed to get all groups from Nacos" }
        emptyList()
    }

    private fun fromNacosInstance(inst: Instance, serviceName: String): InstanceInfo {
        val metadata = inst.metadata
        return InstanceInfo(
            instanceId = metadata.getOrDefault("instanceId", "${inst.ip}:${inst.port}"),
            appGroup   = metadata.getOrDefault("appGroup", serviceName.removePrefix(SERVICE_PREFIX)),
            host       = inst.ip,
            port       = inst.port,
            metadata   = metadata,
            lastHeartbeatMs = System.currentTimeMillis()
        )
    }
}
