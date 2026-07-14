/**
 * Operator-facing management API for workflow function metadata.
 *
 * Bridges the in-memory or DB-backed `FunctionMetaRegistry` SPI with
 * higher-level admin services, exposing a read-only surface that mirrors the
 * shape consumed by the admin console's function catalogue UI.
 *
 * The implementation in [DefaultFunctionMetaManager] simply delegates; the
 * extra layer exists so:
 * * Tests and embedded deployments can swap managers without touching the
 *   underlying registry SPI.
 * * Future managers may add caching, metrics aggregation or cross-registry
 *   fallback logic behind a stable surface.
 */
package com.fluxion.functionmeta.api

import com.fluxion.core.value.FunctionMeta

/**
 * Read-only manager exposing version-aware `FunctionMeta` lookups and
 * listings used by admin tooling and schema pre-flight checks.
 */
interface FunctionMetaManager {

    /**
     * Returns the metadata currently marked as active for `name`.
     *
     * @param name canonical function reference registered with the registry
     * @return active metadata or `null` if no such registration exists
     */
    fun getMeta(name: String): FunctionMeta?

    /**
     * Returns metadata pinned to a specific historical `version`.
     *
     * Used by the admin console's "compare versions" view and by diagnostic
     * tooling that rehydrates old workflow traces.
     *
     * @param name canonical function reference
     * @param version exact version marker requested
     * @return versioned metadata or `null` if the revision is unknown
     */
    fun getMeta(name: String, version: Long): FunctionMeta?

    /**
     * Lists every currently active metadata record.
     *
     * The catalogue UI calls this endpoint on load; callers should be aware
     * that the returned list may be large on tenants with many custom
     * functions and should prefer pagination-backed APIs when available.
     *
     * @return snapshot of active metadata at call time
     */
    fun list(): List<FunctionMeta>

    /**
     * Returns the known version history for a single named function.
     *
     * @param name canonical function reference
     * @return list of version identifiers, newest first where supported
     */
    fun listVersions(name: String): List<Long>

    /**
     * Quick existence check used by admin forms to validate references.
     *
     * @param name canonical function reference to probe
     * @return `true` if at least one active registration exists
     */
    fun contains(name: String): Boolean
}
