package com.fluxion.test.webflux

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.inbound.http.core.RouteConfigStore
import com.fluxion.inbound.http.springmvc.nacos.NacosRouteConfigStore
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Manual `RouteConfigStore` registration for the WebFlux test harness.
 *
 * Background: the standard `NacosRouteConfigStore` / `ApolloRouteConfigStore`
 * auto-configurations are currently scoped to `HttpSpringMvcAdapterAutoConfiguration`,
 * which itself only activates in the presence of Spring MVC's `DispatcherServlet`.
 * WebFlux environments therefore never pick them up automatically -- this
 * configuration closes that gap specifically for the test harness so we can
 * exercise dynamic routes end-to-end under WebFlux.
 */
@Configuration
class TestRouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean(RouteConfigStore::class)
    fun nacosRouteConfigStore(
        configServiceProvider: org.springframework.beans.factory.ObjectProvider<ConfigService>,
        @Value("\${workflow.adapter.http.nacos.data-id:workflow.http.routes}") dataId: String,
        @Value("\${workflow.adapter.http.nacos.group:WORKFLOW}") group: String,
    ): RouteConfigStore? {
        val configService = configServiceProvider.ifAvailable ?: run {
            log.info { "ConfigService not available (nacos profile not active), skipping NacosRouteConfigStore" }
            return null
        }
        log.info { "Creating NacosRouteConfigStore for WebFlux: dataId=$dataId, group=$group" }
        return NacosRouteConfigStore(configService, dataId, group)
    }
}
