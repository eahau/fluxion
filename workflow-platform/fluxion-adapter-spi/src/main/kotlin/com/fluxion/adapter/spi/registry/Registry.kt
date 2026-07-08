package com.fluxion.adapter.spi.registry

/**
 * Runtime instance registry and discovery SPI contracts.
 *
 * Supports a simple service-discovery mechanism where Worker (data-plane)
 * instances self-register with an Admin-facing registry, enabling:
 * - Targeted config push (per app-group or per-instance)
 * - Health monitoring and liveness tracking
 * - Load-aware routing decisions from the control plane
 */

/**
 * Strategy for targeting which worker instances receive a configuration push.
 */
enum class TargetType {
    ALL,
    APP_GROUP,
    INSTANCES
}

/**
 * Opaque descriptor that describes which worker instances a config push
 * should target.
 *
 * Composed with a [TargetType] plus optional filter lists:
 * - ALL: broadcast to every registered instance
 * - APP_GROUP: only instances whose `appGroup` field matches one of [groups]
 * - INSTANCES: only instances whose `instanceId` is in [instanceIds]
 *
 * @param type        Targeting strategy
 * @param groups      App-group filter list (used with APP_GROUP)
 * @param instanceIds Instance-ID filter list (used with INSTANCES)
 */
data class PublishTarget(
    val type: TargetType,
    val groups: List<String> = emptyList(),
    val instanceIds: List<String> = emptyList()
) {
    /**
     * Convenience check: does this target represent a broadcast (ALL)?
     */
    val isAll: Boolean get() = type == TargetType.ALL

    companion object {
        /**
         * Create a broadcast target (all instances).
         */
        @JvmStatic
        fun all(): PublishTarget = PublishTarget(TargetType.ALL)

        /**
         * Create a target scoped to specific application groups.
         *
         * @param groups List of app-group identifiers (defensive copy made)
         */
        @JvmStatic
        fun ofGroups(groups: List<String>): PublishTarget =
            PublishTarget(TargetType.APP_GROUP, groups = groups.toList())

        /**
         * Create a target scoped to specific instance IDs.
         *
         * @param instanceIds List of instance identifiers (defensive copy made)
         */
        @JvmStatic
        fun ofInstances(instanceIds: List<String>): PublishTarget =
            PublishTarget(TargetType.INSTANCES, instanceIds = instanceIds.toList())
    }
}

/**
 * Self-registered runtime instance metadata — stored by [InstanceRegistry]
 * and queried via [InstanceDiscovery].
 *
 * @param instanceId      Globally unique instance identifier (generated on start)
 * @param appGroup        Logical application group (for targeted config push)
 * @param host            Network host address
 * @param port            Listening HTTP port
 * @param metadata        Arbitrary key/value metadata pairs (protocol, version, etc.)
 * @param lastHeartbeatMs Epoch-millis timestamp of the last received heartbeat
 */
data class InstanceInfo(
    val instanceId: String,
    val appGroup: String,
    val host: String,
    val port: Int,
    val metadata: Map<String, String> = emptyMap(),
    val lastHeartbeatMs: Long = System.currentTimeMillis()
) {
    /**
     * Return a copy with an updated heartbeat timestamp.
     *
     * @param heartbeatMs New epoch-millis timestamp
     * @return Updated InstanceInfo
     */
    fun withHeartbeat(heartbeatMs: Long): InstanceInfo = copy(lastHeartbeatMs = heartbeatMs)

    /**
     * Liveness check: is this instance still alive given a timeout window?
     *
     * @param timeoutMs Allowed milliseconds without a heartbeat before considered dead
     * @return true if the last heartbeat is within the timeout window
     */
    fun isAlive(timeoutMs: Long): Boolean =
        (System.currentTimeMillis() - lastHeartbeatMs) < timeoutMs

    companion object {
        /**
         * Convenience factory for minimal InstanceInfo.
         */
        @JvmStatic
        fun of(instanceId: String, appGroup: String, host: String, port: Int): InstanceInfo =
            InstanceInfo(instanceId, appGroup, host, port)
    }
}

/**
 * Worker-side registry — data-plane instances call these methods to report
 * their lifecycle to the control plane.
 */
interface InstanceRegistry {
    /**
     * Register a new instance with the registry.
     *
     * @param instance Full instance metadata
     */
    fun register(instance: InstanceInfo)

    /**
     * Explicitly unregister a previously-registered instance on shutdown.
     *
     * @param instanceId Instance to remove
     */
    fun deregister(instanceId: String)

    /**
     * Report that the instance is still alive.
     *
     * @param instanceId Instance reporting in
     */
    fun heartbeat(instanceId: String)
}

/**
 * Admin-side discovery — control plane components use this to enumerate
 * registered instances and decide push targets.
 */
interface InstanceDiscovery {
    /**
     * List every currently-registered (live) instance.
     */
    fun getAllInstances(): List<InstanceInfo>

    /**
     * List instances that belong to a specific app-group.
     *
     * @param appGroup Group identifier filter
     * @return Matching instances
     */
    fun getInstancesByGroup(appGroup: String): List<InstanceInfo>

    /**
     * List all distinct app-group names currently registered.
     */
    fun getAllGroups(): List<String>
}
