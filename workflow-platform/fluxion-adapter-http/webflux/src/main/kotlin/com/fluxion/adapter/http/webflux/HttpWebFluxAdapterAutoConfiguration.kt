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
 * HTTP adapter auto-configuration for the WebFlux reactive stack.
 *
 * Activated when `WebFluxConfigurer` is on the classpath (reactive
 * deployment). A parallel MVC auto-config exists for servlet deployments.
 *
 * Components wired:
 * 1. [WebFluxRouteRegistry]   — holds route state (CHM), resolves per request.
 * 2. [WebFluxWorkflowHandler] — suspend-based entry point for workflow execution.
 * 3. [WorkflowHandlerAdapter] — bridges `ServerWebExchange` → `ServerRequest/ServerResponse`.
 * 4. [WorkflowHandlerMapping] — Ordered=1 mapping that resolves routes on the fly.
 * 5. `webFluxRouteInitializer` — loads initial routes from [RouteConfigStore] on
 *    ApplicationReadyEvent, then starts the watch stream.
 *
 * Direct-query strategy (vs MVC's `registerMapping`):
 * WebFlux's RouterFunction APIs have no supported runtime mutate operation —
 * rebuilding the composite RouterFunction on every route change would be
 * O(n). Instead we skip RouterFunction entirely: the HandlerMapping resolves
 * the workflow route on EVERY request by scanning routeDefinitions. Route
 * changes become O(1) CHM writes (no lock-contended framework registration)
 * and request resolution is O(n) pattern match against typically <500 routes.
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
     * HandlerAdapter bridges WebFlux exchange → ServerRequest/ServerResponse for our custom handler.
     * Injects HTTP message readers from the shared ServerCodecConfigurer for seamless JSON parsing.
     */
    @Bean
    fun workflowHandlerAdapter(
        codecConfigurer: ServerCodecConfigurer,
    ): WorkflowHandlerAdapter = WorkflowHandlerAdapter(
        messageReaders = codecConfigurer.readers,
    )

    /**
     * HandlerMapping runs per-request route resolution, injected at order=1 in
     * DispatcherHandler's chain (just after RequestMappingHandlerMapping).
     * Stores the resolved RouteMatch into exchange attributes for the handler.
     */
    @Bean
    fun workflowHandlerMapping(
        routeRegistry: WebFluxRouteRegistry,
        handler: WebFluxWorkflowHandler,
    ): HandlerMapping = WorkflowHandlerMapping(routeRegistry, handler)

    /**
     * Boot lifecycle listener. Uses [ObjectProvider] for `RouteConfigStore` so
     * deployments with NO config center (test / embedded) can still start up
     * cleanly without needing a fallback RouteConfigStore bean.
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
