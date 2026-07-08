package com.fluxion.runtime.spring.boot.registry

import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceRegistry
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import java.net.InetAddress
import java.util.UUID

/**
 * Worker instance lifecycle hook.
 *
 * Registers the local Runtime instance with the Admin-facing
 * [InstanceRegistry] during Spring context startup, then deregisters on
 * JVM shutdown via [DisposableBean.destroy].
 *
 * Conditionally wired only when the Worker role is active AND a concrete
 * [InstanceRegistry] bean is available (see [FluxionRuntimeAutoConfiguration]).
 */
class RuntimeInstanceRegistrar(
    private val registry: InstanceRegistry,
    private val env: Environment
) : ApplicationRunner, DisposableBean {

    private lateinit var instanceId: String

    /**
     * Build the [InstanceInfo] descriptor and hand it to the registry.
     *
     * Called by Spring Boot once the application context is fully refreshed.
     * Host resolution order: explicit `workflow.instance.host` env → OS
     * `InetAddress.getLocalHost()` → fallback "127.0.0.1" if the network
     * stack can't supply a routable address.
     */
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

    /**
     * Deregister this instance from the Admin registry on JVM shutdown.
     *
     * The `::instanceId.isInitialized` guard protects against destruction
     * firing before startup completed (e.g. context refresh failure on port
     * already in use).
     */
    override fun destroy() {
        if (::instanceId.isInitialized) {
            registry.deregister(instanceId)
        }
    }

    /**
     * Resolve the routable host advertised to the control plane.
     *
     * Prefers an explicitly-configured override from `workflow.instance.host`
     * so deployments where the OS hostname is misleading (containers, NATed
     * VMs) can be pinned to the correct value.
     *
     * @return Host address string (IP or DNS name)
     */
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
