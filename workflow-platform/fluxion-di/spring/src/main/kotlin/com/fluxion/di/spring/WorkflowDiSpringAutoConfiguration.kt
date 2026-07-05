package com.fluxion.di.spring

import com.fluxion.di.DependencyResolver
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

/**
 * Workflow DI Spring Boot 自动配置
 *
 * 当 classpath 存在 Spring 应用上下文时，自动暴露：
 * - [FunctionInstanceProvider]：用于函数类实例化与依赖注入
 * - [DependencyResolver]：用于脚本函数访问 Spring Bean
 */
@AutoConfiguration
class WorkflowDiSpringAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean(FunctionInstanceProvider::class)
    fun functionInstanceProvider(applicationContext: ApplicationContext): FunctionInstanceProvider {
        log.info { "Registering Spring FunctionInstanceProvider" }
        return SpringFunctionInstanceProvider(applicationContext)
    }

    @Bean
    @ConditionalOnMissingBean(DependencyResolver::class)
    fun dependencyResolver(applicationContext: ApplicationContext): DependencyResolver {
        log.info { "Registering Spring DependencyResolver" }
        return SpringDependencyResolver(applicationContext)
    }
}
