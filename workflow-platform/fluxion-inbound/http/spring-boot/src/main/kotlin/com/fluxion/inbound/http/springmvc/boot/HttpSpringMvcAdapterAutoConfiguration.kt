package com.fluxion.inbound.http.springmvc.boot

import com.alibaba.nacos.api.config.ConfigService as NacosConfigService
import com.ctrip.framework.apollo.ConfigService as ApolloConfigService
import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.inbound.http.core.HttpInboundTriggerMeta
import com.fluxion.inbound.http.core.RouteConfigStore
import com.fluxion.inbound.http.springmvc.apollo.ApolloRouteConfigStore
import com.fluxion.inbound.http.springmvc.handler.MvcWorkflowHandler
import com.fluxion.inbound.http.core.HttpRequestProcessor.ATTR_ROUTE_MATCH
import com.fluxion.inbound.http.springmvc.nacos.NacosRouteConfigStore
import com.fluxion.inbound.http.springmvc.registry.MvcRouteRegistry
import com.fluxion.inbound.spi.InboundRouter
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
 * Unified Spring Boot auto-configuration for the Spring MVC flavour of the
 * HTTP adapter.
 *
 * Assembles three logical groups of beans:
 * 1. **Core MVC dynamic routing** — handler bean, route registry, interceptor,
 *    and ApplicationReadyEvent initialiser that wires everything once Spring
 *    MVC is fully booted (so `RequestMappingHandlerMapping` is ready to accept
 *    dynamic registrations).
 * 2. **Apollo route-config store** — auto-wired when `apollo-client` is on the
 *    classpath (ConditionalOnClass) and no competing `RouteConfigStore` exists.
 * 3. **Nacos route-config store** — auto-wired when Spring Cloud registers a
 *    `Nacos ConfigService` bean (ConditionalOnBean) and no competing store exists.
 *
 * Deployment dependency pattern:
 * ```
 * // Required base (MVC runtime)
 * implementation(project(":workflow-adapter-http-springmvc-spring-boot"))
 * // Pick at most ONE of these two:
 * implementation(project(":workflow-adapter-http-springmvc-apollo"))
 * implementation(project(":workflow-adapter-http-springmvc-nacos"))
 * ```
 *
 * Activation: only runs when `DispatcherServlet` is on the classpath, i.e.
 * Spring MVC servlet stack is being used (vs WebFlux reactive).
 */
@AutoConfiguration(afterName = [
    "com.fluxion.runtime.spring.boot.FluxionRuntimeAutoConfiguration"
])
@ConditionalOnClass(DispatcherServlet::class)
class HttpSpringMvcAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Trigger-function meta descriptor for the MVC HTTP inbound adapter.
     * Exposed to the Admin designer via `TriggerFunctionMetaRegistry` so the
     * user configures HTTP triggers the same way she configures any other
     * workflow-node parameter form.
     */
    @Bean
    @ConditionalOnMissingBean
    fun httpInboundTriggerMeta(): TriggerFunctionMeta = HttpInboundTriggerMeta()

    /**
     * Singleton handler bean — all dynamic routes point to the [MvcWorkflowHandler.handle]
     * method on this bean. Lazy consumers (registries) inject it directly.
     */
    @Bean
    @ConditionalOnMissingBean
    fun mvcWorkflowHandler(
        inboundRouter: InboundRouter
    ): MvcWorkflowHandler = MvcWorkflowHandler(inboundRouter)

    /**
     * MVC-specific route registry.
     *
     * Injecting `RequestMappingHandlerMapping` with `@Lazy` avoids a circular
     * reference during auto-config startup because the handler-mapping is
     * itself initialised by a `BeanPostProcessor` that needs the MVC context
     * to be nearly complete.
     */
    @Bean
    @ConditionalOnMissingBean
    fun mvcRouteRegistry(
        @Lazy requestMappingHandlerMapping: RequestMappingHandlerMapping,
        dynamicHandler: MvcWorkflowHandler
    ): MvcRouteRegistry = MvcRouteRegistry(requestMappingHandlerMapping, dynamicHandler)

    /**
     * Bootstrap lifecycle: on `ApplicationReadyEvent`, perform initial route
     * load then attach the incremental watch stream to the config center.
     *
     * Conditional on a `RouteConfigStore` bean being provided (by the Apollo
     * or Nacos sub-configuration, or by user custom code).
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

    /**
     * Register the route-interceptor on every URL path so pre-resolved
     * [RouteMatch] is available in the request attributes when the handler
     * runs. Order 0 runs before any application-level interceptors.
     */
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
     * Apollo-specific `RouteConfigStore` — activated when `apollo-client` is
     * on the classpath AND no other `RouteConfigStore` has been wired yet.
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
     * Nacos-specific `RouteConfigStore` — activated when Spring Cloud has
     * already registered a `Nacos ConfigService` bean (so we don't force the
     * user to include a hard dep on Nacos just for the adapter to compile).
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
 * Servlet interceptor that pre-resolves the incoming request against the
 * [MvcRouteRegistry] and stores the [RouteMatch] into request attributes.
 *
 * This runs **before** `RequestMappingHandlerMapping` in the interceptor
 * chain (order = 0), so `MvcWorkflowHandler` can read the match without
 * re-executing pattern matching.
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
