package com.fluxion.inbound.http.springmvc.apollo

import com.ctrip.framework.apollo.Config
import com.ctrip.framework.apollo.ConfigChangeListener
import com.fluxion.inbound.http.core.HttpRouteDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.inbound.http.core.RouteChangeListener
import com.fluxion.inbound.http.core.RouteConfigStore
import org.slf4j.*
/**
 * Apollo-backed HTTP route configuration store.
 *
 * After the Admin console publishes HTTP routes, it writes them to Apollo
 * under a configurable namespace (default: `workflow.http.routes`). The
 * actual content is stored under one key (default: `routes`) and supports
 * two JSON shapes at runtime for backwards compatibility:
 *
 * 1. **Raw JSON array** (used when namespace is configured as `json` format):
 *    ```properties
 *    routes=[{"routeKey":"POST:/api/user/login","path":"/api/user/login","method":"POST","workflowId":"user-login-workflow","enabled":true}]
 *    ```
 *
 * 2. **Object wrapper `{routes:[...]}`** (used by properties-format namespaces or
 *    the Apollo UI when pasting a JSON document).
 *
 * @param config    Apollo `Config` instance (auto-injected by Apollo Spring Boot SDK via the ApolloConfiguration in the Spring MVC auto-config).
 * @param routesKey Apollo property key holding the route JSON (default: `routes`).
 */
class ApolloRouteConfigStore(
    private val config:        Config,
    private val routesKey:     String
) : RouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)

    // ----- RouteConfigStore ---------------------------------------------------

    override fun loadAll(): List<HttpRouteDefinition> {
        val value = config.getProperty(routesKey, null)
        return if (value.isNullOrBlank()) {
            log.warn { "Apollo config key [$routesKey] is empty, no routes loaded." }
            emptyList()
        } else {
            parseRoutes(value).also {
                log.info { "Loaded ${it.size} routes from Apollo key [$routesKey]" }
            }
        }
    }

    override fun watch(listener: RouteChangeListener) {
        val changeListener = ConfigChangeListener { changeEvent ->
            if (changeEvent.isChanged(routesKey)) {
                val newValue = changeEvent.getChange(routesKey)?.newValue
                if (newValue.isNullOrBlank()) {
                    log.warn { "Apollo key [$routesKey] changed to empty, clearing all routes." }
                    listener.onRoutesChanged(emptyList())
                } else {
                    try {
                        val routes = parseRoutes(newValue)
                        log.info { "Apollo config change key [$routesKey]: ${routes.size} routes received." }
                        listener.onRoutesChanged(routes)
                    } catch (ex: Exception) {
                        log.error(ex) { "Failed to parse Apollo config change key [$routesKey]" }
                    }
                }
            }
        }
        // Key-scoped listener — Apollo only fires this change-listener when the
        // watched key actually mutates, not for unrelated property churn, which
        // avoids unnecessary diff churn on the registry.
        config.addChangeListener(changeListener, setOf(routesKey))
        log.info { "Started watching Apollo config key [$routesKey] for HTTP route changes." }
    }

    // ----- Parsing -------------------------------------------------------------

    /**
     * Parse either a raw JSON array `[{...}]` OR an object wrapper
     * `{"routes":[{...}]}` into the list of route definitions.
     *
     * Tolerance here means the platform works regardless of whether the
     * operator used the Apollo "JSON namespace" vs "properties namespace"
     * flow when setting up the config.
     */
    private fun parseRoutes(value: String): List<HttpRouteDefinition> {
        val trimmed = value.trim()
        return if (trimmed.startsWith("[")) {
            JsonUtil.deserializeList(trimmed, HttpRouteDefinition::class.java)
        } else {
            data class RoutesWrapper(val routes: List<HttpRouteDefinition> = emptyList())
            JsonUtil.deserialize(trimmed, RoutesWrapper::class.java).routes
        }
    }
}
