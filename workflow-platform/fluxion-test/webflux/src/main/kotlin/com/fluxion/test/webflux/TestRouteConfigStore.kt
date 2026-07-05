package com.fluxion.test.webflux

import com.alibaba.nacos.api.config.ConfigService
import com.fluxion.adapter.http.core.RouteConfigStore
import com.fluxion.adapter.http.springmvc.nacos.NacosRouteConfigStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * WebFlux 测试模块的路由配置存储装配。
 *
 * 由于 NacosRouteConfigStore / ApolloRouteConfigStore 的自动装配目前仅注册在
 * HttpSpringMvcAdapterAutoConfiguration 中（需要 DispatcherServlet），
 * WebFlux 环境不会触发。此处为 WebFlux 手动装配 NacosRouteConfigStore。
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
            log.info("ConfigService not available (nacos profile not active), skipping NacosRouteConfigStore")
            return null
        }
        log.info("Creating NacosRouteConfigStore for WebFlux: dataId=$dataId, group=$group")
        return NacosRouteConfigStore(configService, dataId, group)
    }
}
