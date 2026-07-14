package com.fluxion.di.spring

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.context.ApplicationContext

/**
 * Spring-backed [FunctionInstanceProvider].
 *
 * Instance resolution is a two-stage process, in order:
 *
 * 1. **Lookup as Spring Bean:** if the function class has already been registered in
 *    the application context (e.g. via `@Component`), the existing bean is
 *    returned directly. This preserves AOP proxies, scoped proxies and any
 *    constructor-injected dependencies.
 *
 * 2. **No-arg constructor + autowireBean:** otherwise the function is not yet a
 *    bean, reflectively invoke its no-arg constructor and then call
 *    `AutowireCapableBeanFactory.autowireBean()` on the instance. This covers
 *    the common admin-console pattern where users author classes without Spring
 *    annotations but using field/method injection.
 */
class SpringFunctionInstanceProvider(
    private val applicationContext: ApplicationContext
) : FunctionInstanceProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(functionClass: Class<*>): Boolean = true

    override fun getInstance(functionClass: Class<out WorkflowFunction<*>>): WorkflowFunction<*> {
        val beanNames = applicationContext.getBeanNamesForType(functionClass)
        if (beanNames.isNotEmpty()) {
            if (beanNames.size > 1) {
                log.warn { "Multiple Spring beans found for [${functionClass.name}]: ${beanNames.joinToString()}, using first one" }
            }
            log.debug { "Resolving function [${functionClass.name}] from Spring context as bean [${beanNames[0]}]" }
            return applicationContext.getBean(functionClass)
        }

        log.debug { "Creating function [${functionClass.name}] via no-arg constructor and Spring autowire" }
        val instance = functionClass.getDeclaredConstructor().newInstance()
        applicationContext.autowireCapableBeanFactory.autowireBean(instance)
        return instance
    }
}
