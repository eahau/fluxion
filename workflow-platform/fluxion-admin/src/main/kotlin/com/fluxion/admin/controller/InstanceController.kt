package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.InstancesApi
import com.fluxion.admin.generated.model.AppGroup
import com.fluxion.admin.generated.model.InstanceInfo
import com.fluxion.discovery.core.InstanceDiscovery
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Admin REST API for querying currently registered worker instances.
 *
 * Implements the generated OpenAPI `InstancesApi` interface. The dashboard consumes
 * this data for two primary use cases:
 *   1. Let the publish wizard pick a target app group / instance list.
 *   2. Feed the cluster health panel with per-group live counts.
 *
 * Only registered when the process is running as an admin node
 * (`workflow.instance.role=admin`) because workers do not expose the admin view.
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

    // Flatten the adapter InstanceInfo SPI record into the generated OpenAPI DTO;
    // fields map 1:1, kept explicit so DTO changes surface compile errors.
    private fun toDto(info: com.fluxion.registry.core.InstanceInfo): InstanceInfo = InstanceInfo().apply {
        instanceId = info.instanceId
        appGroup = info.appGroup
        host = info.host
        port = info.port
        metadata = info.metadata.toMutableMap()
        lastHeartbeatMs = info.lastHeartbeatMs
    }

}
