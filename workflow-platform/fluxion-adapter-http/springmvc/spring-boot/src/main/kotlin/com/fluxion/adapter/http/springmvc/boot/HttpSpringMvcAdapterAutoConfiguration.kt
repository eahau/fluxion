package com.fluxion.adapter.http.springmvc.boot

import com.alibaba.nacos.api.config.ConfigService as NacosConfigService
import com.ctrip.framework.apollo.ConfigService as ApolloConfigService
import com.fluxion.adapter.http.core.RouteConfigStore
import com.fluxion.adapter.http.springmvc.apollo.ApolloRouteConfigStore
import com.fluxion.adapter.http.springmvc.handler.MvcWorkflowHandler
import com.fluxion.adapter.http.core.HttpRequestProcessor.ATTR_ROUTE_MATCH
import com.fluxion.adapter.http.springmvc.nacos.NacosRouteConfigStore
import com.fluxion.adapter.http.springmvc.registry.MvcRouteRegistry
import com.fluxion.adapter.spi.WorkflowRouter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * HTTP Spring MVC 适配器统一自动装配入口
 *
 * 装配内容：
 *   - Spring MVC 动态路由核心（handler / registry / interceptor / initializer）
 *   - Apollo 路由配置存储（可选，classpath 存在 apollo-client 时启用）
 *   - Nacos 路由配置存储（可选，Spring 容器中存在 Nacos ConfigService 时启用）
 *
 * 引入方式：
 *   implementation(project(":workflow-adapter-http-springmvc-spring-boot"))
 *   并按需引入 workflow-adapter-http-springmvc-apollo 或 workflow-adapter-http-springmvc-nacos
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet::class)
class HttpSpringMvcAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun mvcWorkflowHandler(
        workflowRouter: WorkflowRouter
    ): MvcWorkflowHandler = MvcWorkflowHandler(workflowRouter)

    @Bean
    @ConditionalOnMissingBean
    fun mvcRouteRegistry(
        @Lazy requestMappingHandlerMapping: RequestMappingHandlerMapping,
        dynamicHandler: MvcWorkflowHandler
    ): MvcRouteRegistry = MvcRouteRegistry(requestMappingHandlerMapping, dynamicHandler)

    /**
     * 启动完成后加载路由并开始监听
     */
    @Bean
    @ConditionalOnBean(RouteConfigStore::class)
    fun workflowRouteInitializer(
        routeConfigStore: RouteConfigStore,
        routeRegistry: MvcRouteRegistry
    ): ApplicationListener<ApplicationReadyEvent> = ApplicationListener { _ ->
        log.info { "Loading initial HTTP routes from RouteConfigStore..." }
        val initial = routeConfigStore.loadAll()
        routeRegistry.init(initial)
        log.info { "Loaded ${initial.size} initial routes, starting config watch..." }
        routeConfigStore.watch(routeRegistry)
    }

    @Bean
    fun workflowMvcConfigurer(routeRegistry: MvcRouteRegistry): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(WorkflowRouteInterceptor(routeRegistry))
                    .addPathPatterns("/**")
                    .order(0)
            }
        }

    /**
     * Apollo 路由配置存储自动装配
     */
    @Configuration
    @ConditionalOnClass(ApolloConfigService::class)
    class ApolloConfiguration {

        @Bean
        @ConditionalOnMissingBean(RouteConfigStore::class)
        fun apolloRouteConfigStore(
            @Value("\${workflow.adapter.http.springmvc.apollo.namespace:workflow.http.routes}") namespace: String,
            @Value("\${workflow.adapter.http.springmvc.apollo.routes-key:routes}") routesKey: String
        ): RouteConfigStore {
            val apolloConfig = ApolloConfigService.getConfig(namespace)
            return ApolloRouteConfigStore(apolloConfig, routesKey)
        }
    }

    /**
     * Nacos 路由配置存储自动装配
     */
    @Configuration
    @ConditionalOnBean(NacosConfigService::class)
    class NacosConfiguration {

        @Bean
        @ConditionalOnMissingBean(RouteConfigStore::class)
        fun nacosRouteConfigStore(
            configService: NacosConfigService,
            @Value("\${workflow.adapter.http.springmvc.nacos.data-id:workflow.http.routes}") dataId: String,
            @Value("\${workflow.adapter.http.springmvc.nacos.group:WORKFLOW}") group: String
        ): RouteConfigStore = NacosRouteConfigStore(configService, dataId, group)
    }
}

/**
 * 工作流路由拦截器
 */
private class WorkflowRouteInterceptor(
    private val registry: MvcRouteRegistry
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val method = request.method
        val path = request.servletPath
        val match = registry.resolveRoute(method, path)
        if (match != null) {
            request.setAttribute(ATTR_ROUTE_MATCH, match)
        }
        return true
    }
}
