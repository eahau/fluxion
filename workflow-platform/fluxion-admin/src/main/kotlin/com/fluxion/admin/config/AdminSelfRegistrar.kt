package com.fluxion.admin.config

import java.net.InetAddress
import java.util.UUID

import org.slf4j.*
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment

import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry

class AdminSelfRegistrar(
    private val registry: InstanceRegistry,
    private val env: Environment
) : ApplicationRunner, DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var instanceId: String

    override fun run(args: ApplicationArguments?) {
        val host = try {
            env.getProperty("workflow.instance.host")?.takeIf { it.isNotBlank() }
                ?: InetAddress.getLocalHost().hostAddress
        } catch (_: Exception) { "127.0.0.1" }
        val appName = env.getProperty("spring.application.name", "fluxion-admin")
        val appGroup = env.getProperty("workflow.instance.app-group", "admin-platform")
        val port = env.getProperty("server.port", Int::class.java, 7070)
        instanceId = "$appName@$host:$port:${UUID.randomUUID().toString().take(8)}"
        val info = InstanceInfo(
            instanceId = instanceId,
            appGroup = appGroup,
            host = host,
            port = port,
            metadata = mapOf(
                "runtime" to "fluxion-admin",
                "appName" to appName,
                "role" to "admin"
            )
        )
        registry.register(info)
        log.info { "Admin self-registered as worker instance: $instanceId" }
    }

    override fun destroy() {
        if (::instanceId.isInitialized) {
            registry.deregister(instanceId)
            log.info { "Admin self-unregistered: $instanceId" }
        }
    }
}
