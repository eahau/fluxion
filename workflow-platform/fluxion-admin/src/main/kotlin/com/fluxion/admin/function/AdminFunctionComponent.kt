package com.fluxion.admin.function

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.WorkflowFunction
import org.springframework.stereotype.Component

/**
 * Declares the admin-internal `WorkflowFunction` implementations contributed via the
 * pluggable `FunctionComponent` SPI.
 *
 * The listed classes are instantiated by `com.fluxion.di.FunctionInstanceProvider` during
 * registry bootstrap, which wires their Spring dependencies and registers them under the
 * `admin:*` function-reference prefixes in `com.fluxion.core.function.FunctionRegistry`.
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
