package com.fluxion.admin.config

import com.fluxion.admin.meta.JdbcFunctionMetaRegistry
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.functionmeta.api.FunctionMetaRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Bridges the admin module's DB-backed function catalogue onto the `FunctionMeta SPI`.
 *
 * Exposes a `JdbcFunctionMetaRegistry` bean that reads from the `wf_function` table via
 * `WfFunctionService`. Spring Boot auto-configuration from
 * `com.fluxion.functionmeta.spring.boot.FunctionMetaAutoConfiguration` will pick this bean
 * up and feed it into the shared `FunctionMetaManager`, allowing runtime workers and the
 * admin console to agree on function metadata without a shared persistence layer.
 */
@Configuration
class FunctionMetaAdminConfiguration {

    @Bean
    @Primary
    fun jdbcFunctionMetaRegistry(functionService: WfFunctionService): FunctionMetaRegistry =
        JdbcFunctionMetaRegistry(functionService)
}
