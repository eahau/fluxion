package com.fluxion.registry.core

import org.springframework.cloud.client.serviceregistry.Registration

data class WorkflowRegistration(
    private val instanceInfo: InstanceInfo
) : Registration {

    override fun getServiceId(): String = instanceInfo.appGroup

    override fun getHost(): String = instanceInfo.host

    override fun getPort(): Int = instanceInfo.port

    override fun isSecure(): Boolean = false

    override fun getMetadata(): Map<String, String> = instanceInfo.metadata

    override fun getInstanceId(): String = instanceInfo.instanceId

    override fun getScheme(): String? = null

    override fun getUri(): java.net.URI = java.net.URI.create("http://${instanceInfo.host}:${instanceInfo.port}")

    fun toInstanceInfo(): InstanceInfo = instanceInfo
}