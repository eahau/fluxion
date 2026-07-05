package com.fluxion.config.nacos

import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.pojo.Instance
import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceRegistry
import org.slf4j.*

/**
 * Nacos 实现 — 实例注册（业务实例侧）
 */
class NacosInstanceRegistry(
    private val namingService: NamingService
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GROUP_NAME = "WORKFLOW"
        private const val SERVICE_PREFIX = "workflow-"
    }

    @Volatile
    private var registeredInstance: InstanceInfo? = null

    override fun register(instance: InstanceInfo) {
        val serviceName = SERVICE_PREFIX + instance.appGroup
        val nacosInstance = toNacosInstance(instance)
        try {
            namingService.registerInstance(serviceName, GROUP_NAME, nacosInstance)
            registeredInstance = instance
            log.info { "Registered instance to Nacos: service=$serviceName instanceId=${instance.instanceId} ${instance.host}:${instance.port}" }
        } catch (e: Exception) {
            throw RuntimeException("Failed to register instance to Nacos: ${instance.instanceId}", e)
        }
    }

    override fun deregister(instanceId: String) {
        val instance = registeredInstance
        if (instance == null || instance.instanceId != instanceId) {
            log.warn { "Deregister request for instanceId=$instanceId ignored: no matching registered instance found" }
            return
        }

        val serviceName = SERVICE_PREFIX + instance.appGroup
        try {
            namingService.deregisterInstance(serviceName, GROUP_NAME, instance.host, instance.port)
            registeredInstance = null
            log.info { "Deregistered instance from Nacos: service=$serviceName instanceId=$instanceId ${instance.host}:${instance.port}" }
        } catch (e: Exception) {
            log.warn(e) { "Failed to deregister instance from Nacos: service=$serviceName instanceId=$instanceId" }
        }
    }

    override fun heartbeat(instanceId: String) {
        log.debug { "Heartbeat for instanceId=$instanceId (managed by Nacos client)" }
    }

    private fun toNacosInstance(info: InstanceInfo): Instance = Instance().apply {
        ip = info.host
        port = info.port
        instanceId = info.instanceId
        isHealthy = true
        isEnabled = true
        metadata = info.metadata + mapOf("instanceId" to info.instanceId, "appGroup" to info.appGroup)
    }
}
