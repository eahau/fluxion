package com.fluxion.admin.function

import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.mapper.WorkflowMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin self-hosted workflow node: publish a workflow definition.
 *
 * Invoked by the built-in meta-workflow referenced by `ADMIN_WORKFLOW_PUBLISH_WORKFLOW_ID`,
 * pushing the publish + route-refresh + worker-side push out of the REST controller layer and
 * into the workflow engine so retries, auditing and side-effects all share one execution path.
 *
 * Exposed as a Spring bean so `com.fluxion.di.spring.SpringFunctionInstanceProvider` can pull
 * it directly from the application context when instantiating the admin function component.
 */
@Component
class AdminPublishWorkflowFunction @Autowired constructor(
    private val workflowService: WfDefinitionService,
    private val workflowMapper: WorkflowMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val workflowId = input.requireWfInput<String>("workflowId")
        val publishTarget = input.wfInput<Map<String, Any>>("publishTarget")?.let {
            JsonUtil.convertValue(it, PublishTarget::class.java)
        }

        log.info { "Admin workflow publishing workflow [$workflowId]" }

        val published = workflowService.publish(workflowId, publishTarget)
        val dto = workflowMapper.toDto(published)

        return FunctionResult.success(dto)
    }

    fun meta() = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: publish a workflow definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:publishWorkflow"
    }
}
