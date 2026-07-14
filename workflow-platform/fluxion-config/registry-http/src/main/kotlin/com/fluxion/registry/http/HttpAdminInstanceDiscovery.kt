package com.fluxion.registry.http

import com.fluxion.config.core.InstanceDiscovery
import com.fluxion.config.core.InstanceInfo

/**
 * HTTP default implementation - Admin-side instance discovery.
 *
 * Queries live instances reported by Workers from [InMemoryInstanceStore],
 * used by Admin internal push configurations and external-facing instance listing.
 */
class HttpAdminInstanceDiscovery(
    private val store: InMemoryInstanceStore
) : InstanceDiscovery {

    override fun getAllInstances(): List<InstanceInfo> = store.getAllInstances()

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        store.getInstancesByGroup(appGroup)

    override fun getAllGroups(): List<String> = store.getAllGroups()
}
