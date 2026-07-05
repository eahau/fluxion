package com.fluxion.adapter.http.webflux

import com.fluxion.adapter.http.core.RouteConfigStore
import com.fluxion.adapter.spi.WorkflowRouter
import org.slf4j.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * HTTP WebFlux adapter auto-configuration.
 *
 * Assembles WebFlux dynamic route components when:
 *   - WebFlux is on the classpath (WebFluxConfigurer present)
 *   - Spring MVC DispatcherServlet is NOT present (avoid conflict)
 *
 * Components:
 *   - [WebFluxWorkflowHandler]: handles incoming requests via ServerRequest/ServerResponse
 *   - [WebFluxRouteRegistry]: manages route definitions (ConcurrentHashMap)
 *   - [WorkflowHandlerMapping]: directly queries registry.resolveRoute() per request
 *   - [WorkflowHandlerAdapter]: bridges exchange → ServerRequest/ServerResponse for the handler
 *   - Route initializer: loads routes from RouteConfigStore on ApplicationReadyEvent
 *
 * Unlike the Spring MVC adapter which uses registerMapping() for incremental O(1)
 * registration, this adapter uses a direct-query strategy: the HandlerMapping resolves
 * the workflow route on every request by scanning routeDefinitions. This eliminates
 * the need for a composite RouterFunction and O(n) rebuild on every route change.
 */
@AutoConfiguration(after = [WebFluxAutoConfiguration::class])
@ConditionalOnClass(WebFluxConfigurer::class)
class HttpWebFluxAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun webFluxRouteRegistry(): WebFluxRouteRegistry = WebFluxRouteRegistry()

    @Bean
    @ConditionalOnMissingBean
    fun webFluxWorkflowHandler(
        workflowRouter: WorkflowRouter,
    ): WebFluxWorkflowHandler = WebFluxWorkflowHandler(workflowRouter)

    /**
     * Custom [org.springframework.web.reactive.HandlerAdapter] that bridges
     * [ServerWebExchange] to [WebFluxWorkflowHandler] via ServerRequest/ServerResponse.
     *
     * Uses [ServerCodecConfigurer] to inject HTTP message readers/writers needed
     * for body parsing and response serialization.
     */
    @Bean
    fun workflowHandlerAdapter(
        codecConfigurer: ServerCodecConfigurer,
    ): WorkflowHandlerAdapter = WorkflowHandlerAdapter(
        messageReaders = codecConfigurer.readers,
    )

    /**
     * [WorkflowHandlerMapping] directly queries [WebFluxRouteRegistry.resolveRoute]
     * for each incoming request, eliminating the RouterFunction intermediate layer.
     *
     * Priority order=1 (after RequestMappingHandlerMapping at 0)
     * to ensure workflow routes are resolved before the default resource handler.
     *
     * The resolved [RouteMatch] is stored in exchange attributes for the handler to read.
     */
    @Bean
    fun workflowHandlerMapping(
        routeRegistry: WebFluxRouteRegistry,
        handler: WebFluxWorkflowHandler,
    ): HandlerMapping = WorkflowHandlerMapping(routeRegistry, handler)

    /**
     * Load initial routes and start watching config center for changes.
     *
     * Uses [ObjectProvider] to defer RouteConfigStore resolution until ApplicationReadyEvent,
     * avoiding bean creation order issues with @ConditionalOnBean.
     */
    @Bean
    fun webFluxRouteInitializer(
        routeConfigStoreProvider: ObjectProvider<RouteConfigStore>,
        routeRegistry: WebFluxRouteRegistry
    ): ApplicationListener<ApplicationReadyEvent> = ApplicationListener { _ ->
        val routeConfigStore = routeConfigStoreProvider.ifAvailable
        if (routeConfigStore == null) {
            log.info { "No RouteConfigStore available, skipping WebFlux route initialization" }
            return@ApplicationListener
        }
        log.info { "Loading initial HTTP routes from RouteConfigStore (WebFlux)..." }
        val initial = routeConfigStore.loadAll()
        routeRegistry.init(initial)
        log.info { "Loaded ${initial.size} initial routes, starting config watch..." }
        routeConfigStore.watch(routeRegistry)
    }
}
