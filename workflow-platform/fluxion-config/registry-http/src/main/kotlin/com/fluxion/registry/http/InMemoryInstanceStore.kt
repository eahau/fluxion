package com.fluxion.registry.http

import com.fluxion.adapter.spi.registry.InstanceInfo
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Admin 侧内存实例存储 — HTTP 自举模式使用
 *
 * 负责保存 Worker 通过 HTTP 注册上来的实例信息，
 * 并定时清理心跳超期的实例。
 */
class InMemoryInstanceStore(
    private val heartbeatTimeoutMs: Long = 30_000L
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val registry = ConcurrentHashMap<String, InstanceInfo>()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "http-registry-cleaner").apply { isDaemon = true }
    }.also { it.scheduleAtFixedRate(::evictExpired, 10, 10, TimeUnit.SECONDS) }

    /** 注册或更新实例 */
    fun register(instance: InstanceInfo) {
        registry[instance.instanceId] = instance
    }

    /** 注销实例 */
    fun deregister(instanceId: String): InstanceInfo? = registry.remove(instanceId)

    /** 更新实例心跳时间 */
    fun heartbeat(instanceId: String) {
        registry.computeIfPresent(instanceId) { _, info ->
            info.withHeartbeat(System.currentTimeMillis())
        }
    }

    /** 获取所有存活实例 */
    fun getAllInstances(): List<InstanceInfo> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) }

    /** 按应用群组获取存活实例 */
    fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) && it.appGroup == appGroup }

    /** 获取所有已注册的应用群组 */
    fun getAllGroups(): List<String> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) }
            .map { it.appGroup }
            .distinct()

    /** 当前注册实例总数（含已过期） */
    fun size(): Int = registry.size

    /**
     * 优雅关闭定时清理器。
     *
     * 由 Spring `@PreDestroy` 或 Bean 的 `destroyMethod` 调用，
     * 防止 JVM 关闭时 daemon 线程仍在操作共享状态。
     */
    fun shutdown() {
        scheduler.shutdown()
        log.info("InMemoryInstanceStore scheduler shut down")
    }

    private fun evictExpired() {
        val before = registry.size
        registry.entries.removeIf { !it.value.isAlive(heartbeatTimeoutMs) }
        val evicted = before - registry.size
        if (evicted > 0) {
            log.info("Evicted $evicted expired instances, remaining: ${registry.size}")
        }
    }
}
