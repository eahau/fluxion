package com.fluxion.registry.http

import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.adapter.spi.registry.InstanceInfo

/**
 * HTTP 默认实现 — Admin 侧实例发现
 *
 * 从 [InMemoryInstanceStore] 中查询 Worker 上报的存活实例，
 * 供 Admin 内部推送配置和对外暴露实例列表使用。
 */
class HttpAdminInstanceDiscovery(
    private val store: InMemoryInstanceStore
) : InstanceDiscovery {

    override fun getAllInstances(): List<InstanceInfo> = store.getAllInstances()

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        store.getInstancesByGroup(appGroup)

    override fun getAllGroups(): List<String> = store.getAllGroups()
}
