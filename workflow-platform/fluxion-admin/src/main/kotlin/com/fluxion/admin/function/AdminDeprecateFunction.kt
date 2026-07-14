package com.fluxion.admin.function

import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin self-hosted workflow node: deprecate (deactivate) a function definition.
 *
 * Invoked by the built-in meta-workflow referenced by `ADMIN_FUNCTION_DEPRECATE_WORKFLOW_ID`,
 * moving the deprecation logic out of the REST controller layer into the workflow engine so
 * audits, retries and side-effects flow through a single execution path.
 *
 * Exposed as a Spring bean so `com.fluxion.di.spring.SpringFunctionInstanceProvider` can pull
 * it directly from the application context when instantiating the admin function component.
 */
@Component
class AdminDeprecateFunction @Autowired constructor(
    private val functionService: WfFunctionService,
    private val functionMapper: FunctionMapper
) : WorkflowFunction<Any?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val functionName = input.requireWfInput<String>("functionName")

        log.info { "Admin workflow deprecating function [$functionName]" }

        val deprecated = functionService.deprecate(functionName)
        val dto = functionMapper.toDto(deprecated)

        return FunctionResult.success(dto)
    }

    fun meta() = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: deprecate a function definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:deprecateFunction"
    }
}
