package com.fluxion.registry.http

import com.fluxion.adapter.spi.registry.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceRegistry
import org.slf4j.LoggerFactory

/**
 * HTTP 默认实现 — Admin 侧实例注册接收器
 *
 * 接收 Worker 通过 HTTP 发送的注册 / 心跳 / 注销请求，
 * 实际实例信息保存在 [InMemoryInstanceStore] 中。
 */
class HttpAdminInstanceRegistry(
    private val store: InMemoryInstanceStore
) : InstanceRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun register(instance: InstanceInfo) {
        store.register(instance)
        log.info("Instance registered: instanceId=${instance.instanceId} appGroup=${instance.appGroup} ${instance.host}:${instance.port}")
    }

    override fun deregister(instanceId: String) {
        val removed = store.deregister(instanceId)
        if (removed != null) {
            log.info("Instance deregistered: instanceId=$instanceId appGroup=${removed.appGroup}")
        }
    }

    override fun heartbeat(instanceId: String) {
        store.heartbeat(instanceId)
        log.debug("Heartbeat received: instanceId=$instanceId")
    }
}
