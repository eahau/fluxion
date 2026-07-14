package com.fluxion.admin.service

import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.core.spi.TriggerFunctionParam
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.slf4j.warn
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/**
 * Collects every [TriggerFunctionMeta] Spring bean contributed by inbound-adapter
 * submodules (HTTP / Kafka / Dubbo / gRPC) and exposes a stable in-memory index to
 * the Admin designer and runtime-trigger router.
 *
 * Resolves [ObjectProvider] lazily on every [listAll] call instead of eagerly caching in
 * the constructor init-block. This avoids false-positive empty registries when the
 * provider is injected before some [TriggerFunctionMeta] beans are fully registered
 * in the application context (Workaround for local Spring bootstrapping order where
 * [WorkflowAdminApplication] `@Bean fun httpInboundTriggerMeta()` occasionally gets
 * materialised after [TriggerFunctionMetaRegistry] construction).
 */
@Service
class TriggerFunctionMetaRegistry(
    private val provider: ObjectProvider<TriggerFunctionMeta>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Resolve all registered trigger metas via the ObjectProvider on every call. */
    private fun resolveAll(): List<TriggerFunctionMeta> {
        val list = provider.orderedStream().toList()
        if (list.isEmpty()) {
            log.warn { "TriggerFunctionMetaRegistry: NO TriggerFunctionMeta beans registered. Designer trigger drawer will show empty state. Verify inbound-spring-boot modules on CP?" }
        } else {
            log.info { "TriggerFunctionMetaRegistry: resolved ${list.size} trigger adapters → ${list.joinToString { it.functionRef }}" }
        }
        return list
    }

    private fun byRefSnapshot(): Map<String, TriggerFunctionMeta> =
        resolveAll().associateBy { it.functionRef }

    /** Immutable snapshot of every registered trigger meta — feed for the designer dropdown. */
    fun listAll(): List<TriggerFunctionMeta> = resolveAll()

    /** Lookup by stable `functionRef`; used by the legacy protocol→trigger bridge. */
    fun findByRef(functionRef: String): TriggerFunctionMeta? = byRefSnapshot()[functionRef]

    /** Lookup by legacy uppercase protocol tag (HTTP / KAFKA / DUBBO / GRPC) — fallback for old data. */
    fun findByLegacyProtocol(legacyProtocol: String): TriggerFunctionMeta? =
        resolveAll().firstOrNull { it.legacyProtocol.equals(legacyProtocol, ignoreCase = true) }

    /**
     * Fill defaults for a newly created trigger instance of `functionRef`. Returns a flat
     * config map with every [TriggerFunctionParam.defaultValue] applied so the UI sees a
     * fully-populated form immediately on "add trigger".
     */
    fun applyDefaults(functionRef: String): Map<String, Any?> {
        val meta = requireNotNull(byRefSnapshot()[functionRef]) {
            "Unknown trigger functionRef=$functionRef; registered=${byRefSnapshot().keys.joinToString()}"
        }
        return meta.paramSchema
            .filter { it.defaultValue != null }
            .associate { it.name to it.defaultValue }
    }
}
