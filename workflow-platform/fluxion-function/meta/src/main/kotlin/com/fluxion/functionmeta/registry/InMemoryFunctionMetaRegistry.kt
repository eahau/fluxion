/**
 * In-memory metadata store used on worker instances.
 *
 * Workers never author metadata directly; the admin side is the source of
 * truth and publishes mutations through a config centre. This registry is
 * the push-side cache kept in sync by `FunctionMetaConfigApplier` at
 * startup and on every subsequent change event.
 *
 * Structural parity with `InMemorySchemaRegistry` in `fluxion-schema` is
 * intentional: both registries share the same write-on-config-push,
 * read-heavy access pattern and use `ConcurrentHashMap` storage so callers
 * can reason about their behaviour consistently.
 */
package com.fluxion.functionmeta.registry

import com.fluxion.core.value.FunctionMeta
import com.fluxion.functionmeta.api.FunctionMetaRegistry
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Push-only in-memory `FunctionMetaRegistry` suitable for worker-side
 * deployments that receive updates from a config centre.
 */
class InMemoryFunctionMetaRegistry : FunctionMetaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    private val metas = ConcurrentHashMap<String, FunctionMeta>()

    private val versions = ConcurrentHashMap<String, Long>()

    override fun get(name: String, version: Long?): FunctionMeta? = metas[name]

    override fun list(): List<FunctionMeta> = metas.values.toList()

    override fun listVersions(name: String): List<Long> =
        versions[name]?.let { listOf(it) } ?: emptyList()

    override fun register(meta: FunctionMeta) {
        val name = meta.functionName
        metas[name] = meta
        log.debug { "Registered function meta: name=$name" }
    }

    override fun unregister(name: String): Boolean {
        versions.remove(name)
        val removed = metas.remove(name) != null
        if (removed) {
            log.info { "Unregistered function meta: name=$name" }
        }
        return removed
    }

    override fun clear() {
        metas.clear()
        versions.clear()
    }

    /** Current active entry count – used by admin health endpoints. */
    fun size(): Int = metas.size
}
