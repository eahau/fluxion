package com.fluxion.schema.api

import com.fluxion.schema.model.Schema

/**
 * Schema registry SPI — lookup of named, versioned schemas by name.
 *
 * Implementations range from the simple in-process [InMemorySchemaRegistry]
 * shipped with the core module to external-backed registries (database,
 * config center, schema registry service). The engine uses the registry for
 * `validateByName` and for resolving `schema:`-prefixed references in
 * workflow definitions.
 *
 * Versioning is optional — the interface takes `version: Long? = null` and
 * returns the latest version when `null` is passed. Implementations that
 * don't track versions (e.g. [InMemorySchemaRegistry]) simply ignore the
 * version parameter and return the latest.
 */
interface SchemaRegistry {

    /**
     * Fetches a schema by name.
     *
     * @param name    schema lookup name (the `schema:<name>` suffix).
     * @param version optional version, `null` = latest.
     * @return the matching [Schema], or `null` if not found.
     */
    fun get(name: String, version: Long? = null): Schema?

    /**
     * Lists every registered schema — one entry per unique name, always the
     * latest version. Used by the admin UI to populate the schema browser.
     */
    fun list(): List<Schema>

    /**
     * Lists all known version numbers for a named schema, ordered oldest to
     * newest. Implementations without version tracking return an empty list
     * (the default).
     */
    fun listVersions(name: String): List<Long> = emptyList()
}
