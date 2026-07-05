package com.fluxion.di.spring

import com.fluxion.core.function.WorkflowFunction
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.context.ApplicationContext

/**
 * Spring 函数实例提供者
 *
 * 实例化 [WorkflowFunction] 实现类时：
 * 1. 优先从当前 [ApplicationContext] 按类型查找已存在的 Spring Bean；
 * 2. 若未注册为 Bean，则反射调用无参构造创建实例，并通过
 *    [org.springframework.beans.factory.config.AutowireCapableBeanFactory.autowireBean]
 *    完成字段/方法级别的依赖注入。
 */
class SpringFunctionInstanceProvider(
    private val applicationContext: ApplicationContext
) : FunctionInstanceProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(functionClass: Class<*>): Boolean = true

    override fun getInstance(functionClass: Class<out WorkflowFunction<*>>): WorkflowFunction<*> {
        // 1. 优先从 Spring 上下文取 Bean
        val beanNames = applicationContext.getBeanNamesForType(functionClass)
        if (beanNames.isNotEmpty()) {
            if (beanNames.size > 1) {
                log.warn { "Multiple Spring beans found for [${functionClass.name}]: ${beanNames.joinToString()}, using first one" }
            }
            log.debug { "Resolving function [${functionClass.name}] from Spring context as bean [${beanNames[0]}]" }
            return applicationContext.getBean(functionClass)
        }

        // 2. 未注册为 Bean 时，反射创建并执行 autowire
        log.debug { "Creating function [${functionClass.name}] via no-arg constructor and Spring autowire" }
        val instance = functionClass.getDeclaredConstructor().newInstance()
        applicationContext.autowireCapableBeanFactory.autowireBean(instance)
        return instance
    }
}
