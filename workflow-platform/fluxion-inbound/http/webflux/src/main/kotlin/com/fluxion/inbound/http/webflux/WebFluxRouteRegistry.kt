package com.fluxion.inbound.http.webflux

import com.fluxion.inbound.http.core.AbstractRouteRegistry
import com.fluxion.inbound.http.core.HttpRouteDefinition
import com.fluxion.inbound.http.core.RouteMatch
import org.slf4j.*
/**
 * WebFlux HTTP route registry — concrete subclass of [AbstractRouteRegistry].
 *
 * WebFlux and Spring MVC differ fundamentally in how they support runtime
 * route mutation:
 * - MVC exposes `RequestMappingHandlerMapping#registerMapping` for O(1)
 *   incremental add/remove of individual routes.
 * - WebFlux's `RouterFunction` API is immutable and requires a full O(n)
 *   composite rebuild on every mutation.
 *
 * WebFlux therefore uses a **direct-query** strategy instead:
 * - Register/unregister writes ONLY go to the parent's `routeDefinitions`
 *   ConcurrentHashMap (O(1), no framework API).
 * - The `WorkflowHandlerMapping` calls [resolveRoute] on EVERY inbound request
 *   to scan `routeDefinitions` for a pattern match. O(n) on the number of
 *   routes but typical deployments stay well under ~500 routes, so the scan
 *   is cheaper than framework-level churn.
 *
 * Pattern matching still uses Spring's shared `PathPattern` (same parser
 * used by Spring MVC) for identical behaviour across stacks.
 */
class WebFluxRouteRegistry : AbstractRouteRegistry() {

    // ----- AbstractRouteRegistry hooks ----------------------------------------

    public override fun activeRouteKeys(): Set<String> = routeDefinitions.keys.toSet()

    override fun doRegister(route: HttpRouteDefinition) {
        // No framework-side registration exists for WebFlux path — we just write
        // into the shared CHM which the HandlerMapping scans per-request.
        onRouteRegistered(route)
        log.info { "Registered WebFlux route: ${route.method} ${route.path} [${route.scope}] → workflowId=[${route.workflowId}]" }
    }

    override fun doUnregister(routeKey: String) {
        if (routeDefinitions.containsKey(routeKey)) {
            onRouteUnregistered(routeKey)
            log.info { "Unregistered WebFlux route: $routeKey" }
        }
    }

    // ----- Request resolution (called by WorkflowHandlerMapping) -------------

    /**
     * Full resolution per-request entry point.
     *
     * Invoked by [WorkflowHandlerMapping] for every WebFlux dispatch. Two-stage:
     * 1. Exact-match against `METHOD:path` key (O(1), no template variables).
     * 2. O(n) scan for `PathPattern` match (returns first fit) with variables.
     *
     * @param method Upper/lower-case HTTP method (normalised internally)
     * @param path   Request URI path (e.g. `/api/users/123`)
     * @return Route match if a registered route fits; null otherwise
     */
    fun resolveRoute(method: String, path: String): RouteMatch? {
        resolveWorkflowId(HttpRouteDefinition.keyOf(method, path))?.let {
            return RouteMatch(it, emptyMap())
        }

        val requestPath = org.springframework.http.server.PathContainer.parsePath(path)
        val patternParser = org.springframework.web.util.pattern.PathPatternParser.defaultInstance

        routeDefinitions.forEach { (routeKey, route) ->
            if (route.method.equals(method, ignoreCase = true)) {
                val pattern = patternParser.parse(route.path)
                val match = pattern.matchAndExtract(requestPath)
                if (match != null) {
                    val workflowId = resolveWorkflowId(routeKey) ?: return@forEach
                    return RouteMatch(workflowId, match.uriVariables)
                }
            }
        }
        return null
    }
}
