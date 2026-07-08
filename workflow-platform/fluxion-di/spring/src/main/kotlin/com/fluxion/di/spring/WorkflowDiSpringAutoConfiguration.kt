package com.fluxion.di.spring

import com.fluxion.di.DependencyResolver
import com.fluxion.di.FunctionInstanceProvider
import org.slf4j.*
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

/**
 * Spring Boot auto-configuration for the DI integration layer.
 *
 * Activated whenever a Spring [ApplicationContext] is present in the classpath.
 * Registers two beans:
 *  * [FunctionInstanceProvider] -- instantiates [WorkflowFunction] classes using
 *    the container (by-type lookup, falling back to no-arg constructor + autowireBean).
 *  * [DependencyResolver] -- allows scripted functions to lookup Spring beans by name or type.
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
