package com.fluxion.admin.function

import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin self-hosted workflow node: publish a function definition.
 *
 * Invoked by the built-in meta-workflow referenced by `ADMIN_FUNCTION_PUBLISH_WORKFLOW_ID`,
 * moving the publish + worker-side push out of the REST controller layer and into the
 * workflow engine so retries, auditing and side-effects share a single execution path.
 *
 * Exposed as a Spring bean so `com.fluxion.di.spring.SpringFunctionInstanceProvider` can pull
 * it directly from the application context when instantiating the admin function component.
 */
@Component
class AdminPublishFunction @Autowired constructor(
    private val functionService: WfFunctionService,
    private val functionMapper: FunctionMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val functionName = input.requireWfInput<String>("functionName")
        val publishTarget = input.wfInput<Map<String, Any>>("publishTarget")?.let {
            JsonUtil.convertValue(it, PublishTarget::class.java)
        }

        log.info { "Admin workflow publishing function [$functionName]" }

        val published = functionService.publish(functionName, publishTarget)
        val dto = functionMapper.toDto(published)

        return FunctionResult.success(dto)
    }

    fun meta() = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: publish a function definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:publishFunction"
    }
}
