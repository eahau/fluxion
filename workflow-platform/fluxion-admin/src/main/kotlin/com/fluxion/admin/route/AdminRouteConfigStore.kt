package com.fluxion.admin.route

import com.fluxion.inbound.http.core.HttpRouteDefinition
import com.fluxion.inbound.http.core.RouteChangeListener
import com.fluxion.inbound.http.core.RouteConfigStore
import com.fluxion.admin.repository.WfDefinitionRepository
import org.slf4j.*
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Admin-internal dynamic HTTP route store backed by the `wf_definition` table.
 *
 * Production behaviour (non-local profiles):
 *   Only HTTP workflows marked `scope = PLATFORM`, `category = META` and `status = ACTIVE`
 *   are loaded, representing the admin console's own meta-workflow endpoints (self-hosted
 *   admin actions that execute inside the admin JVM).
 *
 * Local / dev profile behaviour:
 *   In addition to PLATFORM meta-workflows, every ACTIVE PRIVATE workflow with an HTTP
 *   binding is loaded as well. Local dev boxes typically collocate the admin and Worker
 *   roles, so exposing PRIVATE routes directly here saves running a separate worker
 *   process for ad-hoc end-to-end testing.
 *
 * Implements `ApplicationListener<WorkflowDefinitionRouteRefreshEvent>` so the HTTP adapter
 * reloads its route table whenever a meta-workflow definition is published, deprecated or
 * removed.
 */
@Component
class AdminRouteConfigStore(
    private val definitionRepository: WfDefinitionRepository,
    private val environment: Environment
) : RouteConfigStore, ApplicationListener<WorkflowDefinitionRouteRefreshEvent> {

    private val log = LoggerFactory.getLogger(javaClass)
    private var routeChangeListener: RouteChangeListener? = null

    private val isLocalProfile: Boolean
        get() = environment.activeProfiles.any { it.equals("local", ignoreCase = true) || it.equals("dev", ignoreCase = true) }

    override fun loadAll(): List<HttpRouteDefinition> {
        val definitions = if (isLocalProfile) {
            definitionRepository.findAllActive()
                .filter { it.protocol == "HTTP" && !it.bindKey.isNullOrBlank() }
        } else {
            definitionRepository.findAllActive()
                .filter {
                    it.protocol == "HTTP" &&
                        it.scope == "PLATFORM" &&
                        it.category == "META" &&
                        !it.bindKey.isNullOrBlank()
                }
        }

        log.info {
            "Loaded ${definitions.size} admin HTTP routes from wf_definition " +
                "(isLocal=$isLocalProfile)"
        }

        return definitions.map { def ->
            val method = def.method ?: "POST"
            HttpRouteDefinition(
                routeKey = HttpRouteDefinition.keyOf(method, def.bindKey!!),
                path = def.bindKey!!,
                method = method,
                workflowId = def.workflowId,
                scope = def.scope,
                enabled = true
            )
        }
    }

    /**
     * Load the PLATFORM routes plus every PRIVATE route visible to the supplied `appGroup`.
     * Used when pushing config snapshots to downstream workers.
     */
    fun loadAll(appGroup: String): List<HttpRouteDefinition> {
        val platformRoutes = definitionRepository.findAllActive()
            .filter {
                it.protocol == "HTTP" &&
                    it.scope == "PLATFORM" &&
                    !it.bindKey.isNullOrBlank()
            }

        val privateRoutes = definitionRepository.findAllActiveByScopeAndAppGroup("PRIVATE", appGroup)
            .filter { it.protocol == "HTTP" && !it.bindKey.isNullOrBlank() }

        val all = platformRoutes + privateRoutes
        log.info {
            "Loaded ${all.size} routes for appGroup=[$appGroup]: " +
                "${platformRoutes.size} PLATFORM + ${privateRoutes.size} PRIVATE"
        }

        return all.map { def ->
            val method = def.method ?: "POST"
            HttpRouteDefinition(
                routeKey = HttpRouteDefinition.keyOf(method, def.bindKey!!),
                path = def.bindKey!!,
                method = method,
                workflowId = def.workflowId,
                scope = def.scope,
                enabled = true
            )
        }
    }

    override fun watch(listener: RouteChangeListener) {
        this.routeChangeListener = listener
    }

    override fun onApplicationEvent(event: WorkflowDefinitionRouteRefreshEvent) {
        val listener = routeChangeListener
        if (listener == null) {
            log.debug { "RouteChangeListener not registered yet, skip refreshing admin routes" }
            return
        }
        val routes = loadAll()
        log.info { "Refreshing admin HTTP routes after workflow [${event.workflowId}] changed, loaded ${routes.size} routes" }
        listener.onRoutesChanged(routes)
    }
}
