package com.fluxion.adapter.http.springmvc.nacos

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.fluxion.adapter.http.core.HttpRouteDefinition
import com.fluxion.core.util.JsonUtil
import com.fluxion.adapter.http.core.RouteChangeListener
import com.fluxion.adapter.http.core.RouteConfigStore
import org.slf4j.*
import java.util.concurrent.Executor

/**
 * Nacos-backed HTTP route configuration store.
 *
 * After the Admin console publishes HTTP routes, it writes a JSON snapshot
 * to Nacos under:
 * - dataId:  `workflow.http.routes` (configurable via constructor)
 * - group:   `WORKFLOW` (configurable)
 * - format:  see [HttpRouteDefinition] KDoc for JSON schema
 *
 * This class:
 * 1. **Initial load** does a synchronous `getConfig()` fetch on Worker startup.
 * 2. **Incremental watch** attaches a Nacos `Listener`; every push is parsed
 *    then delivered to the `RouteChangeListener` (the route registry) which
 *    runs the diff and actually registers/unregisters Spring MVC mappings.
 *
 * Example Nacos JSON payload:
 * ```json
 * {
 *   "routes": [
 *     { "routeKey":"POST:/api/user/login",  "path":"/api/user/login",  "method":"POST","workflowId":"user-login-workflow",  "enabled":true },
 *     { "routeKey":"POST:/api/order/create","path":"/api/order/create","method":"POST","workflowId":"create-order-workflow","enabled":true },
 *     { "routeKey":"GET:/api/user/{id}",    "path":"/api/user/{id}",   "method":"GET", "workflowId":"get-user-workflow",    "enabled":true }
 *   ]
 * }
 * ```
 *
 * @param configService Nacos client (provided by `spring-cloud-starter-alibaba-nacos-config`)
 * @param dataId        Nacos DataId of the routes snapshot (default: `workflow.http.routes`)
 * @param group         Nacos group (default: `WORKFLOW`)
 */
class NacosRouteConfigStore(
    private val configService: ConfigService,
    private val dataId:        String,
    private val group:         String
) : RouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)

    private data class RoutesConfig(val routes: List<HttpRouteDefinition> = emptyList())

    // ----- RouteConfigStore ---------------------------------------------------

    override fun loadAll(): List<HttpRouteDefinition> {
        return try {
            val content = configService.getConfig(dataId, group, TIMEOUT_MS)
            if (content.isNullOrBlank()) {
                log.warn { "Nacos config [$group/$dataId] is empty, no routes loaded." }
                emptyList()
            } else {
                parseRoutes(content).also {
                    log.info { "Loaded ${it.size} routes from Nacos [$group/$dataId]" }
                }
            }
        } catch (ex: Exception) {
            log.error(ex) { "Failed to load routes from Nacos [$group/$dataId]" }
            emptyList()
        }
    }

    override fun watch(listener: RouteChangeListener) {
        configService.addListener(dataId, group, object : Listener {

            override fun receiveConfigInfo(configInfo: String?) {
                if (configInfo.isNullOrBlank()) {
                    log.warn { "Received empty config from Nacos [$group/$dataId], clearing all routes." }
                    listener.onRoutesChanged(emptyList())
                    return
                }
                try {
                    val routes = parseRoutes(configInfo)
                    log.info { "Nacos config change [$group/$dataId]: ${routes.size} routes received." }
                    listener.onRoutesChanged(routes)
                } catch (ex: Exception) {
                    log.error(ex) { "Failed to parse Nacos config change [$group/$dataId]" }
                }
            }

            // Nacos callback executor: null means use the SDK's internal thread pool —
            // we let Nacos pick; parsing + diff is cheap and the registry serializes
            // mutations with a ReentrantLock anyway, so no extra isolation is needed.
            override fun getExecutor(): Executor? = null
        })

        log.info { "Started watching Nacos config [$group/$dataId] for HTTP route changes." }
    }

    // ----- Parsing -------------------------------------------------------------

    private fun parseRoutes(content: String): List<HttpRouteDefinition> {
        val config = JsonUtil.deserialize(content, RoutesConfig::class.java)
        return config.routes
    }

    companion object {
        /**
         * Max wait millis for the initial synchronous Nacos `getConfig()` call.
         * Kept short (5s) so Worker startup doesn't hang indefinitely when Nacos
         * is unreachable (partial-availability behaviour: boot with empty routes
         * and wait for the watch stream to recover).
         */
        private const val TIMEOUT_MS = 5_000L
    }
}
