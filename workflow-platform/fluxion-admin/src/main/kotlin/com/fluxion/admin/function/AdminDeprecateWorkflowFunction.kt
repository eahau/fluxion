package com.fluxion.admin.function

import com.fluxion.admin.mapper.WorkflowMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin self-hosted workflow node: deprecate (deactivate) a workflow definition.
 *
 * Invoked by the built-in meta-workflow referenced by `ADMIN_WORKFLOW_DEPRECATE_WORKFLOW_ID`,
 * pushing the deprecation flow down from the REST controller into the workflow engine so
 * audits, retries and side-effects share a single execution path.
 *
 * Exposed as a Spring bean so `com.fluxion.di.spring.SpringFunctionInstanceProvider` can pull
 * it directly from the application context when instantiating the admin function component.
 */
@Component
class AdminDeprecateWorkflowFunction @Autowired constructor(
    private val workflowService: WfDefinitionService,
    private val workflowMapper: WorkflowMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val workflowId = input.requireWfInput<String>("workflowId")

        log.info { "Admin workflow deprecating workflow [$workflowId]" }

        val deprecated = workflowService.deprecate(workflowId)
        val dto = workflowMapper.toDto(deprecated)

        return FunctionResult.success(dto)
    }

    fun meta() = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: deprecate a workflow definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:deprecateWorkflow"
    }
}
