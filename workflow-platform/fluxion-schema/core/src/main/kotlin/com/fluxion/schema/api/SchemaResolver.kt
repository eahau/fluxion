package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaRef

/**
 * Reference resolver — converts a [SchemaRef] (or its string form) into the
 * actual compiled [Schema] it points to.
 *
 * The default implementation in Spring Boot auto-config simply delegates to
 * the configured [SchemaRegistry]; custom resolvers can add cross-service
 * lookup, lazy-fetch from HTTP endpoints, etc.
 */
interface SchemaResolver {

    /**
     * Resolves a typed [SchemaRef].
     *
     * @return the matching schema, or `null` if the ref cannot be resolved.
     */
    fun resolve(ref: SchemaRef): Schema?

    /**
     * Resolves a `format:name` reference string.
     *
     * Default implementation parses via [SchemaRef.parse] and delegates to
     * [resolve]. Returns `null` on malformed refs rather than throwing.
     */
    fun resolve(ref: String): Schema? {
        val schemaRef = SchemaRef.parse(ref) ?: return null
        return resolve(schemaRef)
    }
}
