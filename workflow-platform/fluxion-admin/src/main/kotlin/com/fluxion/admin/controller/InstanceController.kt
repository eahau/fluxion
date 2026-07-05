package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.InstancesApi
import com.fluxion.admin.generated.model.AppGroup
import com.fluxion.admin.generated.model.InstanceInfo
import com.fluxion.adapter.spi.registry.InstanceDiscovery
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * 实例管理 Controller — Admin 后台
 *
 * 实现 OpenAPI 生成的 InstancesApi 接口，提供给前端查询当前已注册的业务实例，用于：
 *   - 发布时选择目标应用群/实例
 *   - 实例监控面板展示
 */
@RestController
@ConditionalOnProperty(name = ["workflow.instance.role"], havingValue = "admin")
class InstanceController(
    private val instanceDiscovery: InstanceDiscovery
) : InstancesApi {

    override fun listInstances(group: String?): ResponseEntity<List<InstanceInfo>> {
        val instances = if (group.isNullOrBlank()) {
            instanceDiscovery.getAllInstances()
        } else {
            instanceDiscovery.getInstancesByGroup(group)
        }
        return ResponseEntity.ok(instances.map { toDto(it) })
    }

    override fun listInstanceGroups(): ResponseEntity<List<AppGroup>> {
        val allInstances = instanceDiscovery.getAllInstances()
        val groups = allInstances.map { it.appGroup }.distinct()
        val groupCounts = allInstances.groupingBy { it.appGroup }.eachCount()
        return ResponseEntity.ok(groups.map { name ->
            AppGroup().apply {
                this.name = name
                instanceCount = groupCounts[name] ?: 0
            }
        })
    }

    private fun toDto(info: com.fluxion.adapter.spi.registry.InstanceInfo): InstanceInfo = InstanceInfo().apply {
        instanceId = info.instanceId
        appGroup = info.appGroup
        host = info.host
        port = info.port
        metadata = info.metadata.toMutableMap()
        lastHeartbeatMs = info.lastHeartbeatMs
    }

}
