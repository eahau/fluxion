package com.fluxion.runtime.spring.boot.registry

import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceRegistry
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import java.net.InetAddress
import java.util.*

/**
 * Runtime 启动时向 Admin 注册自身，关闭时注销。
 *
 * 仅在 workflow.instance.role=worker 时由 FluxionRuntimeAutoConfiguration 条件装配。
 */
class RuntimeInstanceRegistrar(
    private val registry: InstanceRegistry,
    private val env: Environment
) : ApplicationRunner, DisposableBean {

    private lateinit var instanceId: String

    override fun run(args: ApplicationArguments?) {
        val host     = resolveHost()
        val appName  = env.getProperty("spring.application.name", "fluxion-runtime")
        val appGroup = env.getProperty("workflow.instance.app-group", "")
        val port     = env.getProperty("server.port", Int::class.java, 8081)

        instanceId = "$appName@$host:$port:${UUID.randomUUID().toString().take(8)}"

        val info = InstanceInfo(
            instanceId = instanceId,
            appGroup = appGroup,
            host = host,
            port = port,
            metadata = mapOf(
                "runtime" to "fluxion-runtime",
                "appName" to appName
            )
        )

        registry.register(info)
    }

    override fun destroy() {
        if (::instanceId.isInitialized) {
            registry.deregister(instanceId)
        }
    }

    private fun resolveHost(): String {
        val configuredHost = env.getProperty("workflow.instance.host")
        if (!configuredHost.isNullOrBlank()) {
            return configuredHost
        }
        return try {
            InetAddress.getLocalHost().hostAddress
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
