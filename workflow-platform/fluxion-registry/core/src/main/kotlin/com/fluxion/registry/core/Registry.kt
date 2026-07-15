package com.fluxion.registry.core

enum class TargetType {
    ALL,
    APP_GROUP,
    INSTANCES
}

data class PublishTarget(
    val type: TargetType,
    val groups: List<String> = emptyList(),
    val instanceIds: List<String> = emptyList()
) {
    val isAll: Boolean get() = type == TargetType.ALL

    companion object {
        @JvmStatic
        fun all(): PublishTarget = PublishTarget(TargetType.ALL)

        @JvmStatic
        fun ofGroups(groups: List<String>): PublishTarget =
            PublishTarget(TargetType.APP_GROUP, groups = groups.toList())

        @JvmStatic
        fun ofInstances(instanceIds: List<String>): PublishTarget =
            PublishTarget(TargetType.INSTANCES, instanceIds = instanceIds.toList())
    }
}

data class InstanceInfo(
    val instanceId: String,
    val appGroup: String,
    val host: String,
    val port: Int,
    val metadata: Map<String, String> = emptyMap(),
    val lastHeartbeatMs: Long = System.currentTimeMillis()
) {
    fun withHeartbeat(heartbeatMs: Long): InstanceInfo = copy(lastHeartbeatMs = heartbeatMs)

    fun isAlive(timeoutMs: Long): Boolean =
        (System.currentTimeMillis() - lastHeartbeatMs) < timeoutMs

    companion object {
        @JvmStatic
        fun of(instanceId: String, appGroup: String, host: String, port: Int): InstanceInfo =
            InstanceInfo(instanceId, appGroup, host, port)
    }
}

interface InstanceRegistry {
    fun register(instance: InstanceInfo)

    fun deregister(instanceId: String)

    fun heartbeat(instanceId: String)
}