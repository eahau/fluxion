/**
 * Low-level metadata storage SPI used by `FunctionMetaManager` and the
 * config-centre auto-binder.
 *
 * Two production implementations coexist in a typical deployment:
 * * **Admin side** – `JdbcFunctionMetaRegistry` in `fluxion-admin` reads and
 *   writes the `wf_function` table, acting as the source of truth for
 *   operator edits. Published changes flow out to workers via the
 *   `FunctionConfigPublisher` family of classes in `fluxion-config:*`.
 * * **Worker side** – [InMemoryFunctionMetaRegistry] in this module acts as
 *   a push-only cache that the `FunctionMetaConfigApplier` keeps in sync
 *   with config-centre events.
 *
 * The SPI intentionally exposes both a reader and writer surface so the same
 * interface can back DB-backed and memory-backed stores. SchemaRegistry in
 * `fluxion-schema` follows the identical read/write split pattern.
 */
package com.fluxion.functionmeta.api

import com.fluxion.core.value.FunctionMeta

/**
 * Persistence SPI for `FunctionMeta` records.
 *
 * Implementations MUST be safe for concurrent writes from the config-centre
 * binder thread pool and concurrent reads from the engine resolver path.
 */
interface FunctionMetaRegistry {

    /**
     * Retrieves a metadata record by canonical reference.
     *
     * @param name canonical function reference
     * @param version optional version pin; `null` selects the currently
     *   active revision
     * @return stored metadata or `null` if absent
     */
    fun get(name: String, version: Long? = null): FunctionMeta?

    /**
     * Returns every currently active metadata record.
     *
     * Mirrors [FunctionMetaManager.list] on the read side.
     */
    fun list(): List<FunctionMeta>

    /**
     * Enumerates version identifiers for a single function entry.
     */
    fun listVersions(name: String): List<Long>

    /**
     * Inserts or overwrites a metadata record.
     *
     * Called by the admin console on save, by the config-centre applier on
     * every push event, and by the bootstrap loader when materialising
     * seed data.
     *
     * @param meta record to persist
     */
    fun register(meta: FunctionMeta)

    /**
     * Removes all revisions of a named entry.
     *
     * @param name canonical function reference to drop
     * @return `true` if the registry actually mutated
     */
    fun unregister(name: String): Boolean

    /**
     * Wipes all stored records – used in tests and by shutdown hooks on
     * ephemeral worker deployments.
     */
    fun clear()
}
