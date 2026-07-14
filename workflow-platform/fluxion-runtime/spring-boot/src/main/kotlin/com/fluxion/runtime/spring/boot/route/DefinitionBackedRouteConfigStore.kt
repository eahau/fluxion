package com.fluxion.runtime.spring.boot.route

import com.fluxion.config.core.ChangeType
import com.fluxion.config.core.DefinitionChangeListener
import com.fluxion.config.core.DefinitionConfigSubscriber
import com.fluxion.config.core.WorkflowDefinitionSnapshot
import com.fluxion.core.enums.Protocol
import com.fluxion.core.util.JsonUtil
import com.fluxion.inbound.http.core.HttpRouteDefinition
import com.fluxion.inbound.http.core.RouteChangeListener
import com.fluxion.inbound.http.core.RouteConfigStore
import org.slf4j.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [RouteConfigStore] that derives HTTP route definitions directly from the
 * Worker-side [DefinitionConfigSubscriber]. This is the zero-config-centre
 * variant used when neither Apollo nor Nacos is wired up for the Worker
 * (i.e. pure HTTP-push / HTTP-pull mode against the Admin control plane).
 *
 * Route derivation rules (first match wins for each field):
 * 1. Protocol / method / bindKey read from the structured snapshot fields
 *    populated by Admin's `WfDefinitionService.entityToSnapshot` (HTTP DB
 *    columns → Snapshot DTO — default path).
 * 2. Fall back to parsing the same fields out of the raw DAG JSON if the
 *    structured metadata is absent (legacy / config-centre mode).
 * 3. Only HTTP-protocol snapshots are converted to MVC routes; KAFKA/DUBBO/
 *    GRPC bindings use different transport adapters.
 *
 * Snapshot semantics for watch: every change event rebuilds the complete
 * route list and fans it out to every registered [RouteChangeListener].
 * Downstream registries are responsible for diffing against their own
 * live state to compute adds / updates / removes.
 */
class DefinitionBackedRouteConfigStore(
    private val subscriber: DefinitionConfigSubscriber,
    private val appGroup: String? = null
) : RouteConfigStore {

    private val log = LoggerFactory.getLogger(javaClass)
    private val listeners = CopyOnWriteArrayList<RouteChangeListener>()

    /**
     * Bootstrap load — pulls the current full definition snapshot from the
     * config subscriber and materialises the HTTP routes matching this
     * Worker's app-group visibility rules.
     *
     * Any failure here degrades gracefully to an empty route list (the
     * definition subscriber will still surface an error on its own), then
     * subsequent watch events will populate the routes once the Admin
     * server becomes reachable.
     */
    override fun loadAll(): List<HttpRouteDefinition> {
        val snaps = try {
            subscriber.loadAll()
        } catch (e: Exception) {
            log.error(e) { "Failed to load definitions for HTTP route bootstrap" }
            return emptyList()
        }
        return snaps
            .asSequence()
            .filter { it.active && !it.definitionJson.isNullOrBlank() }
            .filter { snap -> matchesAppGroup(snap) }
            .mapNotNull { snap -> snapshotToRoute(snap) }
            .toList()
    }

    /**
     * Attach a [RouteChangeListener] and forward every definition change
     * event as a full route-snapshot notification. Multiple change listeners
     * are supported (e.g. MVC + WebFlux registries on a hybrid server).
     */
    override fun watch(listener: RouteChangeListener) {
        listeners.addIfAbsent(listener)
        subscriber.watch(object : DefinitionChangeListener {
            override fun onChange(key: String, config: WorkflowDefinitionSnapshot, changeType: ChangeType) {
                val routes = loadAll()
                for (l in listeners) {
                    try {
                        l.onRoutesChanged(routes)
                    } catch (e: Exception) {
                        log.error(e) { "Route change listener failed during onChange for key=$key" }
                    }
                }
            }
        })
        log.info { "DefinitionBackedRouteConfigStore watching definitions (appGroup=$appGroup)" }
    }

    /** Apply the same group-filter as [ConfigBackedDefinitionProvider.applySnapshot]. */
    private fun matchesAppGroup(snap: WorkflowDefinitionSnapshot): Boolean {
        if (appGroup == null) return true
        val scope = try {
            snap.definitionJson?.let { JsonUtil.toMap(it) }?.get("scope") as? String
        } catch (e: Exception) {
            null
        }
        if (scope == "PLATFORM") return false
        if (snap.isGlobal()) return true
        return snap.targetGroups?.contains(appGroup) == true
    }

    /**
     * Convert a single definition snapshot → HTTP route DTO. Returns null
     * when the snapshot does not describe an HTTP binding (non-HTTP protocol,
     * missing method/path, or unparsable DAG fallback).
     */
    private fun snapshotToRoute(snap: WorkflowDefinitionSnapshot): HttpRouteDefinition? {
        val dagMap: Map<String, Any>? = try {
            snap.definitionJson?.let { JsonUtil.toMap(it) }
        } catch (e: Exception) {
            log.warn(e) { "Cannot parse definition JSON for route extraction, workflowId=${snap.workflowId}" }
            null
        }

        val protocolStr = (dagMap?.get("protocol") as? String)?.uppercase()
            ?: return null
        if (protocolStr != Protocol.HTTP.name) return null

        val method = (dagMap?.get("method") as? String)?.uppercase()
            ?: return null
        val path = (dagMap?.get("bindKey") as? String)
            ?: return null
        val scope = (dagMap?.get("scope") as? String) ?: "PRIVATE"

        return HttpRouteDefinition(
            routeKey   = HttpRouteDefinition.keyOf(method, path),
            path       = path,
            method     = method,
            workflowId = snap.workflowId,
            scope      = scope,
            enabled    = snap.active
        )
    }
}
