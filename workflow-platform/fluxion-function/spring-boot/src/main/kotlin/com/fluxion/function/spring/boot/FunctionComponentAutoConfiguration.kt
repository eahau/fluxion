package com.fluxion.function.spring.boot

import com.fluxion.core.function.FunctionComponent
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.di.FunctionInstanceProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Spring Boot auto-configuration for function component registration.
 *
 * Bridges the function component SPI with the Spring container by creating a
 * [FunctionComponentConfiguration] bean that discovers and registers all
 * [FunctionComponent] implementations—both as Spring Beans and via Java
 * ServiceLoader (META-INF/services).
 *
 * This is the entry point for function discovery across Admin management nodes
 * and runtime Worker nodes. Built-in functions (via BuiltinConfig) and custom
 * extensions both flow through this mechanism.
 */
@AutoConfiguration
class FunctionComponentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["functionComponentConfiguration"])
    fun functionComponentConfiguration(
        registry: FunctionRegistry,
        components: List<FunctionComponent>,
        instanceProvider: FunctionInstanceProvider?
    ): FunctionComponentConfiguration =
        FunctionComponentConfiguration(registry, components, instanceProvider)
}
