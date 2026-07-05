package com.fluxion.config.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigService
import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.spi.registry.InstanceInfo
import org.slf4j.*

/**
 * Apollo 实现 — 实例发现（Admin 侧）
 */
class ApolloInstanceDiscovery : InstanceDiscovery {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INSTANCES_NAMESPACE = "workflow-instances"
        private const val HEARTBEAT_TIMEOUT_MS = 30_000L
    }

    private val config: Config = ConfigService.getConfig(INSTANCES_NAMESPACE)

    override fun getAllInstances(): List<InstanceInfo> {
        return config.propertyNames.mapNotNull { key ->
            parseInstance(key)?.takeIf { it.isAlive(HEARTBEAT_TIMEOUT_MS) }
        }
    }

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        getAllInstances().filter { it.appGroup == appGroup }

    override fun getAllGroups(): List<String> =
        getAllInstances().map { it.appGroup }.distinct()

    private fun parseInstance(key: String): InstanceInfo? {
        val value = config.getProperty(key, null)
        if (value.isNullOrBlank()) return null
        return try {
            JsonUtil.deserialize(value, InstanceInfo::class.java)
        } catch (e: Exception) {
            log.debug { "Cannot parse instance info for key=$key: ${e.message}" }
            null
        }
    }
}
