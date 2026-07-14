/**
 * Function meta management façade implementations.
 *
 * This package holds the default manager that simply delegates to a plugged
 * [com.fluxion.functionmeta.api.FunctionMetaRegistry], mirroring the
 * `DefaultSchemaManager` pattern used in `fluxion-schema`. Keeping the
 * delegation layer thin means richer cross-cutting concerns (metrics,
 * caching, audit logs) can be composed around the manager without touching
 * registry code that already has its own persistence guarantees.
 */
package com.fluxion.functionmeta

import com.fluxion.core.value.FunctionMeta
import com.fluxion.functionmeta.api.FunctionMetaManager
import com.fluxion.functionmeta.api.FunctionMetaRegistry

/**
 * Thin [FunctionMetaManager] backed by a pluggable [FunctionMetaRegistry].
 *
 * The manager adds no business logic of its own; it exists to give tests
 * and custom deployments a stable surface area independent of registry SPI
 * changes.
 *
 * @param registry storage implementation to delegate to
 */
class DefaultFunctionMetaManager(
    private val registry: FunctionMetaRegistry
) : FunctionMetaManager {

    override fun getMeta(name: String): FunctionMeta? = registry.get(name)

    override fun getMeta(name: String, version: Long): FunctionMeta? = registry.get(name, version)

    override fun list(): List<FunctionMeta> = registry.list()

    override fun listVersions(name: String): List<Long> = registry.listVersions(name)

    override fun contains(name: String): Boolean = registry.get(name) != null
}
