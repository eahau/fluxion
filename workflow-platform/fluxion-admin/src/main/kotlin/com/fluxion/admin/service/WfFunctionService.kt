package com.fluxion.admin.service

import com.fluxion.adapter.spi.config.FunctionConfigPublisher
import com.fluxion.adapter.spi.config.FunctionConfigSnapshot
import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfFunctionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.*
import org.slf4j.error
import org.slf4j.info
import org.springframework.stereotype.Service

/**
 * Management service for registered workflow-function metadata.
 *
 * Responsibilities:
 *   1. CRUD on `wf_function` rows via [WfFunctionRepository]
 *   2. Publish / deprecate state transitions (flip [FunctionStatus] + push to workers)
 *   3. Asynchronously push function-config snapshots to Worker instances via the optional
 *      [FunctionConfigPublisher] SPI (falls back gracefully if no publisher is bound)
 *
 * Snapshot loading intentionally filters out BUILTIN functions — those are registered
 * in-code on the Worker side and do not need to travel over the config distribution
 * channel.
 *
 * Collaborates with: WfFunctionRepository, JsonMapperHelper (JSON ↔ typed map transforms
 * for the unified `config` column), FunctionConfigPublisher (optional worker push).
 */
@Service
class WfFunctionService(
    private val repository: WfFunctionRepository,
    private val jsonMapperHelper: JsonMapperHelper,
    private val functionConfigPublisher: FunctionConfigPublisher? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Transition a function from INACTIVE → ACTIVE and push the snapshot to workers.
     *
     * When an explicit [publishTarget] is supplied it is merged into the persisted config
     * JSON before the publish step so downstream deploy directives are kept alongside the
     * function definition.
     */
    fun publish(
        functionName: String,
        publishTarget: com.fluxion.admin.generated.model.PublishTarget? = null
    ): WfFunction {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: `$functionName") }
        entity.status = FunctionStatus.ACTIVE
        publishTarget?.let {
            val config = (jsonMapperHelper.functionToConfig(entity) ?: emptyMap()).toMutableMap()
            config["publishTarget"] = it
            entity.config = jsonMapperHelper.functionConfigToJsonString(config)
        }
        val saved = repository.save(entity)
        publishToWorkers(saved)
        log.info { "Published function [$functionName]" }
        return saved
    }

    /**
     * Transition a function from ACTIVE → INACTIVE and remove it from every worker.
     */
    fun deprecate(functionName: String): WfFunction {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: `$functionName") }
        entity.status = FunctionStatus.INACTIVE
        val saved = repository.save(entity)
        unpublishFromWorkers(functionName)
        log.info { "Deprecated function [$functionName]" }
        return saved
    }

    /**
     * Persist a function entity and, if the saved row is ACTIVE, immediately push a
     * snapshot to every attached worker.
     */
    fun save(entity: WfFunction): WfFunction {
        val saved = repository.save(entity)
        if (saved.status == FunctionStatus.ACTIVE) {
            publishToWorkers(saved)
        }
        return saved
    }

    /**
     * Delete a function by reference name and synchronously notify every worker.
     * BUILTIN-prefixed names are never pushed via this publisher (they are code-registered),
     * but the DB row is still removed.
     */
    fun delete(functionName: String) {
        val entity = repository.findByFunctionName(functionName)
            .orElseThrow { IllegalArgumentException("Function not found: `$functionName") }
        repository.delete(entity)
        unpublishFromWorkers(functionName)
        log.info { "Deleted function [$functionName]" }
    }

    /**
     * Worker HTTP pull endpoint — returns every enabled non-BUILTIN function snapshot.
     * Used during local development / no-tenant cold-start scenarios.
     */
    fun loadAllEnabledSnapshots(): List<FunctionConfigSnapshot> {
        return repository.findAll()
            .filter { it.status == FunctionStatus.ACTIVE }
            .filterNot { it.functionType.equals("BUILTIN", ignoreCase = true) }
            .map { toSnapshot(it) }
    }

    /**
     * Worker HTTP pull endpoint — returns only PLATFORM + PRIVATE[appGroup] snapshots.
     * Used by per-tenant config distribution.
     */
    fun loadEnabledSnapshots(appGroup: String): List<FunctionConfigSnapshot> {
        val platformFns = repository.findByScope("PLATFORM")
            .filter { it.status == FunctionStatus.ACTIVE && !it.functionType.equals("BUILTIN", ignoreCase = true) }
        val privateFns = repository.findByScopeAndAppGroup("PRIVATE", appGroup)
            .filter { it.status == FunctionStatus.ACTIVE }
        return (platformFns + privateFns).map { toSnapshot(it) }
    }

    /** Single-snapshot lookup used for incremental refresh. */
    fun getSnapshot(functionName: String): FunctionConfigSnapshot? {
        val entity = repository.findByFunctionName(functionName).orElse(null) ?: return null
        return toSnapshot(entity)
    }

    /** Fire-and-forget worker push. BUILTIN functions are skipped (code-registered elsewhere). */
    private fun publishToWorkers(entity: WfFunction) {
        if (entity.functionType.equals("BUILTIN", ignoreCase = true)) return
        val publisher = functionConfigPublisher ?: return
        asyncScope.launch {
            val snapshot = toSnapshot(entity)
            val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
            val generatedTarget = jsonMapperHelper.functionConfigToPublishTargetJson(config)
                ?.let { jsonMapperHelper.stringToPublishTarget(it) }
            val target = jsonMapperHelper.toSpiPublishTarget(generatedTarget)
            try {
                publisher.publish(snapshot, target)
            } catch (e: Exception) {
                log.error(e) { "Failed to publish function [${entity.functionName}] to workers" }
            }
        }
    }

    /** Fire-and-forget worker removal. Skips `builtin:` prefix names (code-registered). */
    private fun unpublishFromWorkers(functionName: String) {
        if (functionName.startsWith("builtin:")) return
        val publisher = functionConfigPublisher ?: return
        asyncScope.launch {
            try {
                publisher.unpublish(functionName)
            } catch (e: Exception) {
                log.error(e) { "Failed to unpublish function [$functionName] from workers" }
            }
        }
    }

    /** Convert a DB entity into the wire-format snapshot expected by the config SPI. */
    private fun toSnapshot(entity: WfFunction): FunctionConfigSnapshot {
        val config = jsonMapperHelper.functionToConfig(entity) ?: emptyMap()
        val targetGroups = jsonMapperHelper.functionConfigToPublishTargetJson(config)
            ?.let { jsonMapperHelper.stringToPublishTarget(it) }?.groups
        return FunctionConfigSnapshot(
            functionName = entity.functionName,
            functionType = entity.functionType,
            scriptBody = jsonMapperHelper.functionConfigToScriptBody(config),
            className = jsonMapperHelper.functionConfigToClassName(config),
            endpoint = null,
            inputSchema = jsonMapperHelper.functionConfigToParamSchema(config),
            outputSchema = jsonMapperHelper.functionConfigToOutputSchema(config),
            description = jsonMapperHelper.functionConfigToDescription(config),
            config = config,
            enabled = entity.status == FunctionStatus.ACTIVE,
            targetGroups = targetGroups
        )
    }
}
