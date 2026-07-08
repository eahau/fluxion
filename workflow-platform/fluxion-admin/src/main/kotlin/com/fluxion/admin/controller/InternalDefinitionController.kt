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
 * Internal HTTP endpoint that serves workflow definition snapshots to workers.
 *
 * Activated by `workflow.config.type=http`. Workers call
 * `GET /internal/workflow/definition/all` on startup and whenever their polling
 * watcher fires to rebuild the local `DefinitionRegistry`.
 *
 * Multi-tenant filtering semantics for the bulk endpoint:
 *   - `appGroup` absent: every `ACTIVE` workflow (PLATFORM + PRIVATE) for legacy callers.
 *   - `appGroup` supplied: `PLATFORM` visibility merged with the PRIVATE workflows of
 *     that specific tenant.
 */
@RestController
@RequestMapping("/internal/workflow/definition")
@ConditionalOnProperty(name = ["workflow.config.type"], havingValue = "http")
class InternalDefinitionController(
    private val definitionService: WfDefinitionService
) {

    /**
     * Bulk definition fetcher used for worker bootstrap / periodic refresh.
     *
     * @param appGroup when non-null only PLATFORM + that tenant's PRIVATE workflows are
     *                 returned; when null all ACTIVE workflows are returned (legacy mode).
     */
    @GetMapping("/all")
    fun loadAll(@RequestParam(required = false) appGroup: String?): List<WorkflowDefinitionSnapshot> {
        return definitionService.loadAllActiveSnapshots(appGroup)
    }

    /**
     * Single definition lookup used for hot-reload / individual workflow refresh.
     */
    @GetMapping("/{workflowId}")
    fun get(@PathVariable workflowId: String): WorkflowDefinitionSnapshot? {
        return definitionService.getSnapshot(workflowId)
    }
}
