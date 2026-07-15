package com.fluxion.discovery.core

import com.fluxion.registry.core.InstanceInfo

interface InstanceDiscovery {
    fun getAllInstances(): List<InstanceInfo>

    fun getInstancesByGroup(appGroup: String): List<InstanceInfo>

    fun getAllGroups(): List<String>
}