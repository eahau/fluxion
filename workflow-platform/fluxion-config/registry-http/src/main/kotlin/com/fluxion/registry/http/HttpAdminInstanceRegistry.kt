/**
 * Admin-side [InstanceRegistry] implementation that accepts worker
 * registration / heartbeat / deregistration calls over HTTP.
 *
 * All mutations are delegated to [InMemoryInstanceStore] which owns the
 * backing [ConcurrentHashMap] and the periodic expired-entry evictor. This
 * class only adds structured audit logging so operators can trace instance
 * lifecycle events through the Admin logs.
 */
package com.fluxion.registry.http

import com.fluxion.config.core.InstanceInfo
import com.fluxion.config.core.InstanceRegistry
import org.slf4j.LoggerFactory
import org.slf4j.*

/**
 * HTTP-facing instance registry used when Admin runs without an external
 * service-discovery system (e.g. no Nacos discovery or Apollo meta-server).
 */
class HttpAdminInstanceRegistry(
    private val store: InMemoryInstanceStore
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun register(instance: InstanceInfo) {
        store.register(instance)
        log.info { "Instance registered: instanceId=${instance.instanceId} appGroup=${instance.appGroup} ${instance.host}:${instance.port}" }
    }

    override fun deregister(instanceId: String) {
        val removed = store.deregister(instanceId)
        if (removed != null) {
            log.info { "Instance deregistered: instanceId=$instanceId appGroup=${removed.appGroup}" }
        }
    }

    override fun heartbeat(instanceId: String) {
        store.heartbeat(instanceId)
        log.debug { "Heartbeat received: instanceId=$instanceId" }
    }
}
