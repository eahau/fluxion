package com.fluxion.adapter.http.core

import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Framework-agnostic HTTP route registry base class.
 *
 * Shared between Spring MVC and WebFlux so the same state-management logic
 * doesn't have to be duplicated. Responsibilities handled here:
 * - Diff algorithm against config-center snapshots (add / update / remove).
 * - `PRIVATE > PLATFORM` scope tie-breaking when a routeKey is published twice.
 * - Thread-safe concurrent state management via `ReentrantLock` + `ConcurrentHashMap`.
 * - Single source of truth for `routeKey → HttpRouteDefinition` mappings.
 *
 * Subclasses only implement three framework-specific hooks:
 * - [doRegister]   — call framework registration API (e.g. `RequestMappingHandlerMapping#registerMapping`
 *                    for Spring MVC, or write to shared state for WebFlux).
 * - [doUnregister] — tear down the framework-side route.
 * - [activeRouteKeys] — return the set of route keys currently live in the
 *                    framework view (for diff computation).
 */
abstract class AbstractRouteRegistry : RouteChangeListener {

    protected val log = LoggerFactory.getLogger(javaClass)

    /**
     * `routeKey → HttpRouteDefinition` — the **single source of truth** for
     * all HTTP route state.
     *
     * `workflowId`, `scope`, `path`, and `method` are all reachable by
     * querying this map directly. There are intentionally no duplicate
     * projection maps (e.g. `workflowId→Set<RouteKey>`) because writes
     * are infrequent (config-center pushes only) and reads are fast enough
     * on `ConcurrentHashMap` to keep the model simple.
     *
     * Mutated exclusively via [onRouteRegistered] / [onRouteUnregistered]
     * — subclasses MUST call those hooks at the end of register/unregister
     * to keep framework state and shared state in sync.
     */
    val routeDefinitions = ConcurrentHashMap<String, HttpRouteDefinition>()

    /**
     * Convenience lookup: resolve `workflowId` by `routeKey`.
     *
     * Thin wrapper around [routeDefinitions] used by the MVC interceptor's
     * exact-match fast path.
     *
     * @param routeKey Canonical `"METHOD:path"` key
     * @return workflowId bound to the key, or null if unbound
     */
    fun resolveWorkflowId(routeKey: String): String? = routeDefinitions[routeKey]?.workflowId

    protected val lock = ReentrantLock()

    // ----- RouteChangeListener: diff algorithm ---------------------------------

    /**
     * Config-center push entry point — called for every snapshot.
     *
     * Computes and applies the diff under a single `ReentrantLock` so the
     * framework view and `routeDefinitions` cannot drift apart if two
     * config-center notifications interleave. Sequence inside the lock:
     * 1. Filter disabled routes and dedupe by scope.
     * 2. Remove routes no longer present in the new snapshot.
     * 3. Register new routes and re-register routes whose `workflowId` changed
     *    (unregister + register pair guarantees framework state is consistent).
     *
     * Routes with identical `routeKey` + `workflowId` between old and new
     * snapshots are SKIPPED to avoid pointless framework churn.
     */
    override fun onRoutesChanged(snapshot: List<HttpRouteDefinition>) {
        lock.withLock {
            val deduped = dedupeRoutes(snapshot)
            val newKeys = deduped.map { it.routeKey }.toSet()
            val oldKeys = activeRouteKeys()

            val toRemove = oldKeys - newKeys
            toRemove.forEach { doUnregister(it) }

            deduped.forEach { route ->
                val existing = routeDefinitions[route.routeKey]?.workflowId
                when {
                    existing == null -> doRegister(route)
                    existing != route.workflowId -> {
                        doUnregister(route.routeKey)
                        doRegister(route)
                    }
                }
            }

            log.info {
                val added = newKeys - oldKeys
                val removed = toRemove
                "Route sync done: +${added.size} added, -${removed.size} removed, ${activeRouteKeys().size} total active"
            }
        }
    }

    /**
     * Bootstrap helper — populate the registry on Worker startup.
     *
     * Called by the auto-config after the Spring context reaches
     * `ApplicationReadyEvent` (so `RequestMappingHandlerMapping` is fully
     * initialised for the MVC path).
     *
     * @param initialRoutes First snapshot returned by `RouteConfigStore.loadAll()`
     */
    fun init(initialRoutes: List<HttpRouteDefinition>) {
        log.info { "Loading ${initialRoutes.size} initial HTTP routes..." }
        onRoutesChanged(initialRoutes)
    }

    // ----- Template methods: framework-specific hooks --------------------------

    /**
     * Return the set of `routeKey`s currently live in the framework view.
     *
     * MVC subclasses can read directly from [routeDefinitions].
     * WebFlux does the same (no intermediate framework state to query).
     *
     * @return Live route-key snapshot for diff computation
     */
    protected abstract fun activeRouteKeys(): Set<String>

    /**
     * Framework-side registration.
     *
     * Implementations MUST call [onRouteRegistered] AFTER the framework API
     * succeeds so the shared `routeDefinitions` map stays consistent.
     *
     * @param route Route to register
     */
    protected abstract fun doRegister(route: HttpRouteDefinition)

    /**
     * Framework-side removal by `routeKey`.
     *
     * Implementations MUST call [onRouteUnregistered] AFTER the framework API
     * succeeds so the shared `routeDefinitions` map stays consistent.
     *
     * @param routeKey Canonical key of the route to drop
     */
    protected abstract fun doUnregister(routeKey: String)

    // ----- Shared internal state management hooks -----------------------------

    /**
     * Subclasses must call at the END of a successful framework registration
     * (inside [doRegister]) so shared state reflects the framework view.
     */
    protected fun onRouteRegistered(route: HttpRouteDefinition) {
        routeDefinitions[route.routeKey] = route
    }

    /**
     * Subclasses must call at the END of a successful framework deregistration
     * (inside [doUnregister]) so shared state reflects the framework view.
     */
    protected fun onRouteUnregistered(routeKey: String) {
        routeDefinitions.remove(routeKey)
    }

    /**
     * Diagnostic snapshot — `routeKey → workflowId` map for health endpoints
     * and Admin status dashboards.
     *
     * @return Immutable snapshot of the registry contents (defensive copy)
     */
    fun registeredRoutes(): Map<String, String> =
        routeDefinitions.mapValues { it.value.workflowId }

    // ----- Private helpers -----------------------------------------------------

    /**
     * Deduplicate routes published under both scopes to the same `routeKey`.
     *
     * Policy: `PRIVATE` always wins over `PLATFORM` because the PRIVATE-scope
     * publish is usually a tenant-specific override of a shared platform
     * route. `MARKETPLACE` routes fall through to the first (arbitrary) pick
     * since they are never published alongside a conflicting PRIVATE variant.
     *
     * @return De-duplicated list where each `routeKey` appears exactly once
     */
    private fun dedupeRoutes(snapshot: List<HttpRouteDefinition>): List<HttpRouteDefinition> =
        snapshot
            .filter { it.enabled }
            .groupBy { it.routeKey }
            .map { (_, routes) ->
                routes.firstOrNull { it.scope == "PRIVATE" } ?: routes.first()
            }
}
