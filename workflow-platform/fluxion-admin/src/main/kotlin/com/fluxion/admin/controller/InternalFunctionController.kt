package com.fluxion.admin.controller

import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.admin.service.WfFunctionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Internal HTTP endpoint that exposes enabled function metadata to worker nodes.
 *
 * Part of the lightweight admin-as-config-server path activated by
 * `workflow.config.type=http`. Workers call `GET /internal/workflow/function/all` once
 * at bootstrap, or periodically if the polling watcher is enabled, to populate their
 * `FunctionMetaRegistry` and validate step inputs before execution.
 */
@RestController
@RequestMapping("/internal/workflow/function")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalFunctionController(
    private val functionService: WfFunctionService
) {

    /**
     * Return every function snapshot currently marked as `ENABLED` in the admin DB.
     */
    @GetMapping("/all")
    fun loadAll(): List<FunctionConfigSnapshot> {
        return functionService.loadAllEnabledSnapshots()
    }

    /**
     * Return a single function snapshot by canonical `functionName`.
     */
    @GetMapping("/{functionName}")
    fun get(@PathVariable functionName: String): FunctionConfigSnapshot? {
        return functionService.getSnapshot(functionName)
    }
}
