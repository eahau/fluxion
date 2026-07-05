package com.fluxion.admin.controller

import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.admin.service.WfFunctionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 内部函数分发端点 — HTTP 模式下供 Worker 拉取函数配置
 *
 * 仅在 workflow.config.type=http 时激活。
 * Worker 启动时调用 GET /internal/workflow/function/all 全量拉取。
 */
@RestController
@RequestMapping("/internal/workflow/function")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalFunctionController(
    private val functionService: WfFunctionService
) {

    /**
     * 全量拉取所有已启用函数配置快照
     */
    @GetMapping("/all")
    fun loadAll(): List<FunctionConfigSnapshot> {
        return functionService.loadAllEnabledSnapshots()
    }

    /**
     * 获取单个函数配置快照
     */
    @GetMapping("/{functionName}")
    fun get(@PathVariable functionName: String): FunctionConfigSnapshot? {
        return functionService.getSnapshot(functionName)
    }
}
