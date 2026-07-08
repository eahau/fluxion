package com.fluxion.adapter.http.core

/**
 * HTTP route configuration source SPI.
 *
 * Framework-agnostic contract for how Worker instances discover the current
 * set of published HTTP routes. Implementations live in integration-specific
 * sibling modules:
 * - `NacosRouteConfigStore`  (in `fluxion-adapter-http-springmvc-nacos`)
 * - `ApolloRouteConfigStore` (in `fluxion-adapter-http-springmvc-apollo`)
 * - Extensible to Zookeeper watches, Redis pub/sub, DB polling, etc.
 *
 * Wiring chain:
 * ```
 * Config Center (Apollo/Nacos)
 *   → RouteConfigStore impl (parses JSON → List<HttpRouteDefinition>)
 *     → HttpRouteRegistry (MVC or WebFlux impl, does diff/register/unregister)
 *       → Framework-specific registration
 *         (RequestMappingHandlerMapping#registerMapping for MVC,
 *          direct ConcurrentHashMap write for WebFlux)
 * ```
 *
 * Admin publish flow that populates the backing store:
 * 1. Admin saves `wf_definition` with `bindKey` + `protocol=HTTP`.
 * 2. Admin writes a JSON route snapshot to the config center namespace.
 * 3. This SPI's watcher fires and notifies the live Worker registry.
 * 4. Each Worker diffs the snapshot and registers/unregisters framework routes.
 */
interface RouteConfigStore {

    /**
     * Bootstrap load — returns the full set of enabled routes the first
     * time Worker comes up, before the incremental watch stream is attached.
     *
     * @return Initial list of route definitions (may be empty on fresh deploy)
     */
    fun loadAll(): List<HttpRouteDefinition>

    /**
     * Attach a change listener for incremental config-center pushes.
     *
     * Every callback receives the **full current snapshot** of routes; the
     * registry implementation is responsible for diffing against its live
     * state to compute adds/updates/removes (snapshot semantics avoids
     * dropped-update bugs).
     *
     * @param listener Registry callback to invoke on each change notification
     */
    fun watch(listener: RouteChangeListener)
}

/**
 * Change listener contract — implemented by the route registry and invoked
 * from a `RouteConfigStore` whenever the config center publishes a new
 * HTTP route snapshot.
 */
fun interface RouteChangeListener {

    /**
     * Fired once per config-center change notification.
     *
     * Implementations (registries) diff the snapshot against their live
     * state and apply additions, updates, and removals accordingly.
     *
     * @param snapshot Complete current list of enabled routes
     */
    fun onRoutesChanged(snapshot: List<HttpRouteDefinition>)
}
