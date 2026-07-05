package com.fluxion.admin.controller

import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.admin.service.WfSchemaService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 内部 Schema 分发端点 — HTTP 模式下供 Worker 拉取 Schema 配置
 *
 * 仅在 workflow.config.type=http 时激活。
 * Worker 启动时调用 GET /internal/workflow/schema/all 全量拉取。
 */
@RestController
@RequestMapping("/internal/workflow/schema")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalSchemaController(
    private val schemaService: WfSchemaService
) {

    /**
     * 全量拉取所有 Schema 配置快照
     */
    @GetMapping("/all")
    fun loadAll(): List<SchemaConfigSnapshot> {
        return schemaService.loadAllSchemaSnapshots()
    }

    /**
     * 获取单个 Schema 配置快照
     */
    @GetMapping("/{schemaName}")
    fun get(@PathVariable schemaName: String): SchemaConfigSnapshot? {
        return schemaService.getSnapshot(schemaName)
    }
}
