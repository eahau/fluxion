package com.fluxion.admin.controller

import com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot
import com.fluxion.admin.service.WfDefinitionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 内部定义分发端点 — HTTP 模式下供 Worker 拉取定义
 *
 * 仅在 workflow.config.type=http 时激活。
 * Worker 启动时调用 GET /internal/workflow/definition/all 全量拉取。
 *
 * 支持按 appGroup 过滤：
 *   - 未指定 appGroup 时：返回所有 ACTIVE 工作流（兼容旧行为）
 *   - 指定 appGroup 时：返回 PLATFORM + 该 appGroup 的 PRIVATE 工作流
 */
@RestController
@RequestMapping("/internal/workflow/definition")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalDefinitionController(
    private val definitionService: WfDefinitionService
) {

    /**
     * 拉取定义快照。
     * @param appGroup 所属应用分组，传入时仅下发 PLATFORM + 该 appGroup 的 PRIVATE
     */
    @GetMapping("/all")
    fun loadAll(@RequestParam(required = false) appGroup: String?): List<WorkflowDefinitionSnapshot> {
        return definitionService.loadAllActiveSnapshots(appGroup)
    }

    /**
     * 获取单个定义快照
     */
    @GetMapping("/{workflowId}")
    fun get(@PathVariable workflowId: String): WorkflowDefinitionSnapshot? {
        return definitionService.getSnapshot(workflowId)
    }
}
