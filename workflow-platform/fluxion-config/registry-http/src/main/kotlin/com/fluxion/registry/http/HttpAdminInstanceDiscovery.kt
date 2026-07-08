package com.fluxion.registry.http

import com.fluxion.adapter.spi.registry.InstanceDiscovery
import com.fluxion.adapter.spi.registry.InstanceInfo

/**
 * HTTP 榛樿瀹炵幇 鈥?Admin 渚у疄渚嬪彂鐜?
 *
 * 浠?[InMemoryInstanceStore] 涓煡璇?Worker 涓婃姤鐨勫瓨娲诲疄渚嬶紝
 * 渚?Admin 鍐呴儴鎺ㄩ€侀厤缃拰瀵瑰鏆撮湶瀹炰緥鍒楄〃浣跨敤銆?
 */
class HttpAdminInstanceDiscovery(
    private val store: InMemoryInstanceStore
) : InstanceDiscovery {

    override fun getAllInstances(): List<InstanceInfo> = store.getAllInstances()

    override fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        store.getInstancesByGroup(appGroup)

    override fun getAllGroups(): List<String> = store.getAllGroups()
}
