package com.fluxion.admin.function

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.WorkflowFunction
import org.springframework.stereotype.Component

/**
 * Admin 内部函数组件。
 *
 * 将 admin 自举所需的工作流节点函数类注册到 [com.fluxion.core.function.FunctionRegistry]，
 * 由 [com.fluxion.di.FunctionInstanceProvider] 在注册阶段实例化并完成依赖注入。
 */
@Component
class AdminFunctionComponent : FunctionComponent {

    override fun componentName() = "admin-functions"

    override fun functionClasses(): List<Class<out WorkflowFunction<*>>> = listOf(
        AdminPublishFunction::class.java,
        AdminDeprecateFunction::class.java,
        AdminPublishWorkflowFunction::class.java,
        AdminDeprecateWorkflowFunction::class.java
    )
}
