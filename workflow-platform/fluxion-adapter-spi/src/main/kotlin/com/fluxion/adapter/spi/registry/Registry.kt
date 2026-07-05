package com.fluxion.adapter.spi.registry

/**
 * 发布目标类型枚举
 */
enum class TargetType {
    /** 全量广播（所有实例） */
    ALL,
    /** 按应用群组（指定群组内所有实例） */
    APP_GROUP,
    /** 按具体实例（指定实例 ID 列表） */
    INSTANCES
}

/**
 * 发布目标 — 指定工作流定义推送的范围
 */
data class PublishTarget(
    val type: TargetType,
    val groups: List<String> = emptyList(),
    val instanceIds: List<String> = emptyList()
) {
    /** 是否全量广播 */
    val isAll: Boolean get() = type == TargetType.ALL

    companion object {
        /** 全量广播（所有实例） */
        @JvmStatic
        fun all(): PublishTarget = PublishTarget(TargetType.ALL)

        /** 按应用群组发布 */
        @JvmStatic
        fun ofGroups(groups: List<String>): PublishTarget =
            PublishTarget(TargetType.APP_GROUP, groups = groups.toList())

        /** 按具体实例发布 */
        @JvmStatic
        fun ofInstances(instanceIds: List<String>): PublishTarget =
            PublishTarget(TargetType.INSTANCES, instanceIds = instanceIds.toList())
    }
}

/**
 * 业务实例元信息
 */
data class InstanceInfo(
    val instanceId: String,
    val appGroup: String,
    val host: String,
    val port: Int,
    val metadata: Map<String, String> = emptyMap(),
    val lastHeartbeatMs: Long = System.currentTimeMillis()
) {
    /** 返回带有更新心跳时间的新实例 */
    fun withHeartbeat(heartbeatMs: Long): InstanceInfo = copy(lastHeartbeatMs = heartbeatMs)

    /** 判断实例是否存活（心跳未超时） */
    fun isAlive(timeoutMs: Long): Boolean =
        (System.currentTimeMillis() - lastHeartbeatMs) < timeoutMs

    companion object {
        /** 创建实例信息（心跳时间取当前） */
        @JvmStatic
        fun of(instanceId: String, appGroup: String, host: String, port: Int): InstanceInfo =
            InstanceInfo(instanceId, appGroup, host, port)
    }
}

/**
 * 实例注册接口 — 业务实例侧
 *
 * 业务应用启动时调用 register() 向注册中心注册自身，
 * 运行期间定时发送心跳，应用关闭时 deregister() 注销。
 *
 * 实现约束：
 *   - register() 必须幂等（重复注册不产生副作用）
 *   - deregister() 不存在时静默忽略
 *   - 网络失败应抛出 RuntimeException，由调用方决定是否重试
 */
interface InstanceRegistry {
    /** 注册实例到注册中心 */
    fun register(instance: InstanceInfo)

    /** 从注册中心注销实例 */
    fun deregister(instanceId: String)

    /** 发送心跳续约 */
    fun heartbeat(instanceId: String)
}

/**
 * 实例发现接口 — Admin 侧
 *
 * Admin 后台通过此接口查询当前存活的业务实例，
 * 用于定向发布和管理面板展示。
 *
 * 实现约束：
 *   - getAllInstances() 应只返回存活（心跳未过期）的实例
 *   - 返回结果为当前时刻的快照，不保证实时一致性
 */
interface InstanceDiscovery {
    /** 获取所有存活实例 */
    fun getAllInstances(): List<InstanceInfo>

    /** 按应用群组获取实例 */
    fun getInstancesByGroup(appGroup: String): List<InstanceInfo>

    /** 获取所有已注册的应用群组名称 */
    fun getAllGroups(): List<String>
}
