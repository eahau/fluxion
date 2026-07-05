package com.fluxion.admin.function

import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Admin 自举工作流节点函数：下线函数定义。
 *
 * 由内置工作流 [ADMIN_FUNCTION_DEPRECATE_WORKFLOW_ID] 调用，
 * 将函数下线逻辑从 Controller 下沉到工作流引擎执行。
 *
 * 作为 Spring Bean 注册，由 [com.fluxion.di.spring.SpringFunctionInstanceProvider]
 * 从应用上下文中直接获取实例。
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

    override fun meta(): FunctionMeta = FunctionMeta.builder(FUNCTION_REF)
        .description("Admin self-hosted workflow: deprecate a function definition")
        .build()

    companion object {
        const val FUNCTION_REF = "admin:deprecateFunction"
    }
}
