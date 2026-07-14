/**
 * Default [SchemaResolver] that resolves Fluxion-standard references
 * (`schema:<name>`, `protobuf:<name>`, `avro:<name>`) against the local
 * [SchemaRegistry].
 *
 * The current implementation ignores the format-prefix portion of the ref
 * and delegates purely on [SchemaRef.name] + optional [SchemaRef.version];
 * future iterations may tighten the contract by using the prefix to assert
 * the resolved schema's format matches expectations.
 */
package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.api.SchemaResolver
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaRef

/**
 * Resolves [SchemaRef] pointers via the injected [SchemaRegistry].
 */
class FluxionSchemaResolver(
    private val registry: SchemaRegistry
) : SchemaResolver {

    override fun resolve(ref: SchemaRef): Schema? {
        return registry.get(ref.name, ref.version)
    }
}
