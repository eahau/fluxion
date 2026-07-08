package com.fluxion.admin.controller

import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.admin.service.WfSchemaService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Internal HTTP endpoint that exposes JSON Schema config snapshots to worker nodes.
 *
 * Only active when the admin process itself is used as a lightweight config server via
 * `workflow.config.type=http`; in production deployments a real config center (Nacos,
 * Consul, ...) usually replaces this path entirely. On bootstrap each worker calls
 * `GET /internal/workflow/schema/all` to seed its local in-memory registry.
 */
@RestController
@RequestMapping("/internal/workflow/schema")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalSchemaController(
    private val schemaService: WfSchemaService
) {

    /**
     * Return every published Schema snapshot for bulk worker bootstrapping.
     */
    @GetMapping("/all")
    fun loadAll(): List<SchemaConfigSnapshot> {
        return schemaService.loadAllSchemaSnapshots()
    }

    /**
     * Return a single Schema snapshot by canonical name (worker hot-reload lookups).
     */
    @GetMapping("/{schemaName}")
    fun get(@PathVariable schemaName: String): SchemaConfigSnapshot? {
        return schemaService.getSnapshot(schemaName)
    }
}
