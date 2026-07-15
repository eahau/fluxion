package com.fluxion.registry.spring.cloud

import com.fluxion.registry.core.InstanceInfo
import com.fluxion.registry.core.InstanceRegistry
import com.fluxion.registry.core.WorkflowRegistration
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.serviceregistry.ServiceRegistry
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.util.CollectionUtils

class SpringCloudInstanceRegistry(
    private val serviceRegistry: ServiceRegistry<Registration>,
    private val discoveryClient: DiscoveryClient
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var registeredRegistration: WorkflowRegistration? = null

    override fun register(instance: InstanceInfo) {
        val registration = WorkflowRegistration(instance)
        serviceRegistry.register(registration)
        registeredRegistration = registration
        log.info { "Registered instance via Spring Cloud: serviceId=${instance.appGroup} instanceId=${instance.instanceId} ${instance.host}:${instance.port}" }
    }

    override fun deregister(instanceId: String) {
        val registration = registeredRegistration
        if (registration == null || registration.instanceId != instanceId) {
            log.warn { "Deregister request for instanceId=$instanceId ignored: no matching registered instance found" }
            return
        }
        serviceRegistry.deregister(registration)
        registeredRegistration = null
        log.info { "Deregistered instance via Spring Cloud: instanceId=$instanceId" }
    }

    override fun heartbeat(instanceId: String) {
        log.debug { "Heartbeat for instanceId=$instanceId (managed by Spring Cloud ServiceRegistry)" }
    }
}