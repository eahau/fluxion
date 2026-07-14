/**
 * In-memory worker instance store for the Admin-side HTTP self-registration mode.
 *
 * Holds [InstanceInfo] records pushed by workers through the HTTP registry endpoints,
 * and periodically evicts entries whose heartbeat has exceeded [heartbeatTimeoutMs]
 * so that crashed or network-partitioned workers are eventually removed from the
 * available-pool view exposed to [HttpInstanceRegistry].
 */
package com.fluxion.registry.http

import com.fluxion.config.core.InstanceInfo
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * In-memory instance storage backend used by the HTTP registry when running
 * in single-process / self-hosted Admin mode.
 *
 * The store is thread-safe: writes from HTTP handlers and reads from the
 * periodic eviction task both go through [ConcurrentHashMap]. A dedicated
 * daemon scheduler evicts expired entries every 10 seconds; callers should
 * invoke [shutdown] from `@PreDestroy` / bean `destroyMethod` to avoid
 * lingering daemon threads during JVM shutdown.
 *
 * @property heartbeatTimeoutMs max allowed millis since the last successful
 *                              heartbeat before an instance is considered dead.
 */
class InMemoryInstanceStore(
    private val heartbeatTimeoutMs: Long = 30_000L
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val registry = ConcurrentHashMap<String, InstanceInfo>()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "http-registry-cleaner").apply { isDaemon = true }
    }.also { it.scheduleAtFixedRate(::evictExpired, 10, 10, TimeUnit.SECONDS) }

    /**
     * Register (or refresh) a worker instance.
     *
     * @param instance the snapshot reported by the worker; existing entries
     *                 with the same [InstanceInfo.instanceId] are overwritten.
     */
    fun register(instance: InstanceInfo) {
        registry[instance.instanceId] = instance
    }

    /**
     * Explicitly remove a worker instance (clean deregistration).
     *
     * @return the previously stored [InstanceInfo] if one existed, `null` otherwise.
     */
    fun deregister(instanceId: String): InstanceInfo? = registry.remove(instanceId)

    /**
     * Touch the heartbeat timestamp of an existing instance.
     *
     * Missing instance ids are silently ignored — they will be purged by the
     * next eviction cycle if they were actually registered earlier.
     */
    fun heartbeat(instanceId: String) {
        registry.computeIfPresent(instanceId) { _, info ->
            info.withHeartbeat(System.currentTimeMillis())
        }
    }

    /**
     * Return the currently alive (non-expired) instances across all groups.
     */
    fun getAllInstances(): List<InstanceInfo> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) }

    /**
     * Return alive instances filtered by a specific application group.
     *
     * @param appGroup the group name as advertised in [InstanceInfo.appGroup].
     */
    fun getInstancesByGroup(appGroup: String): List<InstanceInfo> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) && it.appGroup == appGroup }

    /**
     * Return distinct application group names that have at least one alive instance.
     */
    fun getAllGroups(): List<String> =
        registry.values.filter { it.isAlive(heartbeatTimeoutMs) }
            .map { it.appGroup }
            .distinct()

    /**
     * Total number of tracked entries including stale ones pending eviction.
     */
    fun size(): Int = registry.size

    /**
     * Gracefully shut down the periodic eviction scheduler.
     *
     * Intended to be called from Spring `@PreDestroy` or a bean destroy-method
     * so that the daemon thread does not race with JVM shutdown hooks while
     * mutating shared map state.
     */
    fun shutdown() {
        scheduler.shutdown()
        log.info { "InMemoryInstanceStore scheduler shut down" }
    }

    private fun evictExpired() {
        val before = registry.size
        registry.entries.removeIf { !it.value.isAlive(heartbeatTimeoutMs) }
        val evicted = before - registry.size
        if (evicted > 0) {
            log.info { "Evicted $evicted expired instances, remaining: ${registry.size}" }
        }
    }
}
