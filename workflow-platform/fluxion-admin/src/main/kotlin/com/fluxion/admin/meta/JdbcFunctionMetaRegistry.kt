package com.fluxion.admin.meta

import com.fluxion.core.value.FunctionMeta
import com.fluxion.functionmeta.api.FunctionMetaRegistry
import com.fluxion.admin.service.WfFunctionService
import org.slf4j.*
/**
 * Admin-side `FunctionMetaRegistry` that treats the `wf_function` table as the single
 * source of truth (SSOT) for function metadata.
 *
 * Contract with surrounding subsystems:
 *   - **Write path**: admins mutate `wf_function` exclusively through
 *     `WfFunctionService` (via `save` / `publish` / `deprecate`), which updates the
 *     database and then publishes change events downstream.
 *   - **Read path**: `FunctionMetaManager` (from the functionmeta module) consults this
 *     registry to answer "what metadata does function X have?" for admin-hosted flows.
 *   - **Push path**: after a DB mutation, `WfFunctionService` hands the new metadata to
 *     `FunctionConfigPublisher`, which in turn serializes it and pushes it out to every
 *     worker so their `FunctionMetaConfigApplier` mirrors the change without hitting the DB.
 *
 * Because the database owns the state, `register` / `unregister` / `clear` are no-ops here
 * (they exist solely to satisfy the SPI contract); log statements record the call so
 * operators can diagnose unexpected registry write attempts.
 */
class JdbcFunctionMetaRegistry(
    private val functionService: WfFunctionService
) : FunctionMetaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun get(name: String, version: Long?): FunctionMeta? =
        functionService.getSnapshot(name)

    override fun list(): List<FunctionMeta> =
        functionService.loadAllEnabledSnapshots()

    override fun listVersions(name: String): List<Long> = emptyList()

    override fun register(meta: FunctionMeta) {
        log.debug { "JdbcFunctionMetaRegistry.register ignored (DB is SSOT): name=${meta.functionName}" }
    }

    override fun unregister(name: String): Boolean {
        log.debug { "JdbcFunctionMetaRegistry.unregister ignored (DB is SSOT): name=$name" }
        return false
    }

    override fun clear() {
    }
}
