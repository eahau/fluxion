package com.fluxion.admin.service

import com.fluxion.builtin.db.workflow.DbExecuteWorkflowCompiler
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.exception.WorkflowException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowTrigger
import com.fluxion.core.model.TriggerType
import com.fluxion.core.util.DagTopology
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.config.core.DefinitionConfigPublisher
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.config.core.PublishTarget as SpiPublishTarget
import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.admin.route.WorkflowDefinitionRouteRefreshEvent
import com.fluxion.admin.util.toPage
import org.slf4j.*
import org.slf4j.debug
import org.slf4j.error
import org.slf4j.info
import org.slf4j.warn
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Workflow definition management service and runtime [DefinitionProvider].
 *
 * Responsibilities:
 *   1. CRUD on the `wf_definition` table (DRAFT / ACTIVE / DEPRECATED lifecycle)
 *   2. Convert DB-persisted JSON to the runtime [WorkflowDefinition] domain model
 *   3. Caffeine-backed local caching of ACTIVE definitions and protocol bindings
 *   4. Push definition snapshots to Worker nodes via the optional
 *      [DefinitionConfigPublisher] SPI on publish/deprecate mutations
 *   5. Fire Spring [WorkflowDefinitionRouteRefreshEvent]s when META-category
 *      workflows change, so the admin auto-generated route registry stays in sync
 *   6. Pre-compile DAGs: normalize node types, inject default logging decorators,
 *      validate function references (scope-aware), and eagerly compile `dbExecute`
 *      nodes against upstream input schemas
 *
 * Collaborates with: WfDefinitionRepository (persistence), WfFunctionRepository
 * (function ref validation), FunctionRegistry (builtin function lookups),
 * DefinitionConfigPublisher (optional worker push), DbExecuteWorkflowCompiler
 * (SQL-node AOT compilation), ApplicationEventPublisher (META-route refresh).
 */
@Service
class WfDefinitionService(
    private val repository: WfDefinitionRepository,
    private val functionRepository: WfFunctionRepository,
    private val functionRegistry: FunctionRegistry,
    private val configPublisher: DefinitionConfigPublisher? = null,
    private val jsonMapperHelper: JsonMapperHelper,
    private val eventPublisher: ApplicationEventPublisher? = null
) : DefinitionProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Emit a route-refresh Spring application event when the mutated definition is
     * category META. These definitions drive admin auto-generated REST routes.
     */
    private fun notifyRouteRefreshIfMeta(entity: WfDefinition) {
        if (entity.category == "META") {
            eventPublisher?.publishEvent(WorkflowDefinitionRouteRefreshEvent(this, entity.workflowId))
        }
    }

    /**
     * Create or update a workflow definition.
     *
     * Pipeline: normalize DAG node types → inject default logging decorators →
     * (if ACTIVE) validate DAG JSON + referenced function availability + pre-compile
     * dbExecute nodes → persist → (if ACTIVE) push to config center → notify route
     * refresh listeners for META workflows.
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun save(entity: WfDefinition): WfDefinition {
        entity.dagJson = normalizeDagJson(entity.dagJson)
        entity.dagJson = applyDefaultLoggingDecorator(entity.dagJson)
        if (entity.status == "ACTIVE") {
            validateDagJson(entity.workflowId, entity.dagJson)
            validateFunctionRefs(entity.dagJson, entity.appGroup)
            compileDbExecuteNodes(entity)
        }
        val saved = repository.save(entity)
        if (saved.status == "ACTIVE") {
            publishToConfigCenter(saved)
        }
        notifyRouteRefreshIfMeta(saved)
        return saved
    }

    /**
     * Publish a workflow (DRAFT → ACTIVE) and bump its version number.
     *
     * Runs a strict pre-publish pipeline: validate DAG structure, verify referenced
     * functions are ACTIVE and scope-visible, pre-compile dbExecute nodes, then
     * re-inject the default logging decorator on top (because the compile step may
     * have inserted new nodes). If a [publishTarget] is supplied it overrides any
     * previously stored target-group directive. Post-save: push to config center
     * and — for META workflows — emit the route refresh event.
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun publish(
        workflowId: String,
        publishTarget: com.fluxion.admin.generated.model.PublishTarget? = null
    ): WfDefinition {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: `$workflowId") }
        validateDagJson(workflowId, entity.dagJson)
        validateFunctionRefs(entity.dagJson, entity.appGroup)
        compileDbExecuteNodes(entity)
        entity.dagJson = applyDefaultLoggingDecorator(entity.dagJson)
        entity.status = "ACTIVE"
        entity.version += 1
        publishTarget?.let {
            entity.publishTarget = jsonMapperHelper.publishTargetToString(it)
        }
        val saved = repository.save(entity)
        publishToConfigCenter(saved)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Published workflow [$workflowId] version [${entity.version}]" }
        return saved
    }

    /**
     * Deprecate a workflow (ACTIVE → DEPRECATED) and revoke it from workers.
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun deprecate(workflowId: String): WfDefinition {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: `$workflowId") }
        entity.status = "DEPRECATED"
        val saved = repository.save(entity)
        unpublishFromConfigCenter(workflowId)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Deprecated workflow [$workflowId]" }
        return saved
    }

    /**
     * Push a definition snapshot to the optional config-distribution channel.
     *
     * Failure is intentionally non-fatal: a publish error degrades gracefully to
     * "local-only" (the DB row is committed), and the next publish / worker HTTP
     * pull cycle will retry. Target resolution: prefer the explicit `publishTarget`
     * stored on the entity, fall back to the deprecated `targetGroups` column.
     */
    private fun publishToConfigCenter(entity: WfDefinition) {
        val publisher = configPublisher ?: run {
            log.debug { "DefinitionConfigPublisher not available, skip publishing workflow [${entity.workflowId}]" }
            return
        }
        try {
            val generatedTarget = jsonMapperHelper.stringToPublishTarget(entity.publishTarget)
                ?: jsonMapperHelper.fallbackPublishTarget(entity.targetGroups)
            val target = jsonMapperHelper.toSpiPublishTarget(generatedTarget)
            publisher.publish(entity.workflowId, entity.dagJson, entity.version, target)
        } catch (e: Exception) {
            log.error(e) { "Failed to publish workflow [${entity.workflowId}] to config center: ${e.message}" }
        }
    }

    /**
     * Revoke a definition from every connected worker. Safe no-op if no publisher
     * SPI is bound or if the workflow was never published.
     */
    private fun unpublishFromConfigCenter(workflowId: String) {
        val publisher = configPublisher ?: run {
            log.debug { "DefinitionConfigPublisher not available, skip unpublishing workflow [$workflowId]" }
            return
        }
        try {
            publisher.unpublish(workflowId)
        } catch (e: Exception) {
            log.error(e) { "Failed to unpublish workflow [$workflowId] from config center: ${e.message}" }
        }
    }

    /**
     * Runtime [DefinitionProvider] lookup. Returns only ACTIVE definitions and
     * consults the `wfDefinition` Caffeine cache for hot paths.
     */
    @Cacheable(cacheNames = ["wfDefinition"], key = "#key")
    override fun get(key: String): WorkflowDefinition? {
        return repository.findByWorkflowId(key)
            .filter { it.status == "ACTIVE" }
            .map { toWorkflowDefinition(it) }
            .orElse(null)
    }

    /** Raw entity fetch (status-agnostic) used by admin UI edit views. */
    fun getEntity(workflowId: String): WfDefinition? {
        return repository.findByWorkflowId(workflowId).orElse(null)
    }

    /** Raw entity fetch by primary key ID. */
    fun getEntityById(id: Long): WfDefinition? {
        return repository.findById(id).orElse(null)
    }

    /**
     * Returns the [WorkflowDefinition] domain object regardless of status. Used
     * by debug / trace tooling that needs to replay DRAFT executions.
     */
    fun getDefinitionAnyStatus(workflowId: String): WorkflowDefinition? {
        return repository.findByWorkflowId(workflowId)
            .map { toWorkflowDefinition(it) }
            .orElse(null)
    }

    /**
     * Scope-aware route-binding conflict check.
     *
     * Conflict rules (matches the Worker runtime binding semantics):
     *   - PRIVATE scope: only conflicts with other PRIVATE workflows in the same `appGroup`
     *   - PLATFORM / MARKETPLACE scope: conflicts globally with workflows in the same scope
     *
     * Skips the check entirely when `bindKey` is blank (workflow has no HTTP/gRPC route).
     * Callers performing an UPDATE pass [excludeWorkflowId] so a workflow does not
     * conflict with its own existing binding.
     */
    fun findRouteConflict(
        protocol: String,
        method: String?,
        bindKey: String?,
        scope: String = "PRIVATE",
        appGroup: String? = null,
        excludeWorkflowId: String? = null
    ): WfDefinition? {
        if (bindKey.isNullOrBlank()) return null

        val conflict = if (scope == "PRIVATE") {
            repository.findByScopeAndAppGroupAndProtocolAndMethodAndBindKey(
                "PRIVATE", appGroup, protocol, method, bindKey
            ).orElse(null)
        } else {
            repository.findByScopeAndProtocolAndMethodAndBindKey(
                scope, protocol, method, bindKey
            ).orElse(null)
        }

        return conflict?.takeIf { it.workflowId != excludeWorkflowId }
    }

    /**
     * Back-compat overload for callers written before scope isolation landed.
     * Defaults to PRIVATE scope with no app-group restriction.
     */
    @Deprecated("Use findRouteConflict with scope parameter", ReplaceWith("findRouteConflict(protocol, method, bindKey, scope, appGroup, excludeWorkflowId)"))
    fun findRouteConflict(
        protocol: String,
        method: String?,
        bindKey: String?,
        excludeWorkflowId: String? = null
    ): WfDefinition? {
        return findRouteConflict(protocol, method, bindKey, "PRIVATE", null, excludeWorkflowId)
    }

    /**
     * Paginated admin search over workflow definitions.
     * Keyword matches case-insensitively against workflowId or workflowName.
     */
    fun search(keyword: String?, pageable: Pageable): Page<WfDefinition> {
        val all = if (keyword.isNullOrBlank()) {
            repository.findAll()
        } else {
            val lower = keyword.lowercase()
            repository.findAll().filter {
                it.workflowId.lowercase().contains(lower) ||
                    it.workflowName.lowercase().contains(lower)
            }
        }
        return all.toPage(pageable)
    }

    /**
     * Delete a definition by id. Also revokes it from the config distribution
     * channel and refreshes META routes if applicable.
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun delete(workflowId: String) {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: `$workflowId") }
        repository.delete(entity)
        unpublishFromConfigCenter(workflowId)
        notifyRouteRefreshIfMeta(entity)
        log.info { "Deleted workflow [$workflowId]" }
    }

    /**
     * Rollback placeholder. Current semantics: flip the latest row back to DRAFT
     * and bump the version counter. Row-level version history is planned but not
     * yet implemented on the persistence side.
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun rollback(workflowId: String, version: Int): WfDefinition {
        val current = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: `$workflowId") }
        current.status = "DRAFT"
        current.version = version + 1
        val saved = repository.save(current)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Rolled back workflow [$workflowId] to version [${saved.version}]" }
        return saved
    }

    /**
     * Historical versions for a workflow. Current implementation scans every row
     * with the matching business id and sorts by the integer version column. True
     * row-level immutable versioning is a future enhancement.
     */
    fun findVersions(workflowId: String): List<WfDefinition> {
        return repository.findAll()
            .filter { it.workflowId == workflowId }
            .sortedBy { it.version }
    }

    /**
     * Bulk-export a set of workflow definitions as a minimal OpenAPI 3.0 document.
     *
     * Each workflow is exported as `POST /<workflowId>` with JSON request/response
     * bodies derived from the input/output schema columns. When `ids` is empty the
     * export includes every ACTIVE definition in the DB.
     */
    fun exportOpenAPI(ids: List<String>, title: String?, version: String?): Map<String, Any> {
        val workflows = if (ids.isEmpty()) repository.findAllActive() else ids.mapNotNull { getEntity(it) }
        val doc = mutableMapOf<String, Any>(
            "openapi" to "3.0.3",
            "info" to mapOf(
                "title" to (title ?: "Workflow Export"),
                "version" to (version ?: "1.0.0")
            ),
            "paths" to workflows.associate { wf ->
                val path = "/${wf.workflowId}"
                path to mapOf(
                    "post" to mapOf(
                        "summary" to wf.workflowName,
                        "operationId" to wf.workflowId,
                        "requestBody" to mapOf(
                            "content" to mapOf(
                                "application/json" to mapOf(
                                    "schema" to (wf.inputSchema?.let { JsonUtil.toMap(it) } ?: emptyMap<String, Any>())
                                )
                            )
                        ),
                        "responses" to mapOf(
                            "200" to mapOf(
                                "description" to "success",
                                "content" to mapOf(
                                    "application/json" to mapOf(
                                        "schema" to (wf.outputSchema?.let { JsonUtil.toMap(it) } ?: emptyMap<String, Any>())
                                    )
                                )
                            )
                        )
                    )
                )
            }
        )
        return doc
    }

    /** Engine bootstrap preload: every ACTIVE definition as domain objects. */
    fun loadAllActive(): List<WorkflowDefinition> {
        return repository.findAllActive().map { toWorkflowDefinition(it) }
    }

    /**
     * Worker HTTP pull: all ACTIVE workflow snapshots.
     *
     * When `appGroup` is supplied the result follows the Worker visibility contract:
     *   - All PLATFORM-scope ACTIVE definitions
     *   - PRIVATE-scope ACTIVE definitions whose appGroup matches
     *
     * When `appGroup` is null every ACTIVE definition is returned (back-compat for
     * single-tenant local-dev deployments).
     */
    fun loadAllActiveSnapshots(appGroup: String? = null): List<com.fluxion.config.core.WorkflowDefinitionSnapshot> {
        val entities = if (appGroup.isNullOrBlank()) {
            repository.findAllActive()
        } else {
            val platform = repository.findAllActiveByScope("PLATFORM")
            val private = repository.findAllActiveByScopeAndAppGroup("PRIVATE", appGroup)
            platform + private
        }
        return entities.map { entity ->
            com.fluxion.config.core.WorkflowDefinitionSnapshot.of(
                entity.workflowId, entity.dagJson, entity.version, true
            )
        }
    }

    /** Single-definition snapshot lookup used for incremental worker refresh. */
    fun getSnapshot(workflowId: String): com.fluxion.config.core.WorkflowDefinitionSnapshot? {
        return repository.findByWorkflowId(workflowId)
            .filter { it.status == "ACTIVE" }
            .map { entity ->
                com.fluxion.config.core.WorkflowDefinitionSnapshot.of(
                    entity.workflowId, entity.dagJson, entity.version, true
                )
            }
            .orElse(null)
    }

    /**
     * Resolve a protocol + bindKey route to a workflow id. Result is cached in
     * Caffeine (`wfBinding` cache) for hot path invocation.
     */
    @Cacheable(cacheNames = ["wfBinding"], key = "#protocol + ':' + #bindKey")
    override fun findByBinding(protocol: String, bindKey: String): String? {
        return repository.findByProtocolAndBindKey(protocol, bindKey)
            .filter { it.status == "ACTIVE" }
            .map { it.workflowId }
            .orElse(null)
    }

    /**
     * Convert the persisted DB row to the [WorkflowDefinition] runtime domain object.
     *
     * Unmarshalling steps:
     *   1. Parse `dagJson` → extract `nodes` → map to [com.fluxion.core.model.Node] via JsonMapperHelper
     *   2. Parse `triggersConfig` → list of [WorkflowTrigger] (WEBHOOK / SCHEDULED / MANUAL / etc.)
     *   3. Copy scalar columns (id, name, version, schemas, scope, errorHandlerRef) verbatim
     */
    private fun toWorkflowDefinition(entity: WfDefinition): WorkflowDefinition {
        val dagData = JsonUtil.toMap(entity.dagJson)

        val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
        val nodes = jsonMapperHelper.parseNodesFromMap(nodesJson)

        val triggers = parseTriggers(entity.triggersConfig)

        return WorkflowDefinition(
            id = entity.workflowId,
            name = entity.workflowName,
            nodes = nodes,
            inputSchema = entity.inputSchema,
            inputSchemaFormat = entity.inputSchemaFormat.takeIf { it.isNotBlank() },
            outputSchema = entity.outputSchema,
            outputSchemaFormat = entity.outputSchemaFormat.takeIf { it.isNotBlank() },
            version = entity.version,
            status = entity.status,
            errorHandlerRef = entity.errorHandlerRef,
            scope = entity.scope,
            appGroup = entity.appGroup,
            triggers = triggers
        )
    }

    /**
     * Parse the optional `triggersConfig` JSON column into a list of
     * [WorkflowTrigger]. Expected shape:
     *
     * ```
     * [{"id":"t1","type":"WEBHOOK","config":{"path":"/hooks/x"},"enabled":true}]
     * ```
     *
     * Unknown `type` strings fall back to [TriggerType.MANUAL] instead of failing
     * the whole workflow load (defensive default). Corrupt JSON similarly degrades
     * gracefully to an empty trigger list.
     */
    private fun parseTriggers(triggersConfig: String?): List<WorkflowTrigger> {
        if (triggersConfig.isNullOrBlank()) return emptyList()
        return try {
            val triggerMaps = JsonUtil.deserializeList(triggersConfig, Map::class.java)
                ?.filterIsInstance<Map<String, Any>>()
                ?: return emptyList()
            triggerMaps.map { m ->
                WorkflowTrigger(
                    id = m["id"]?.toString() ?: "",
                    type = try { TriggerType.valueOf(m["type"]?.toString() ?: "MANUAL") } catch (_: Exception) { TriggerType.MANUAL },
                    config = m["config"].uncheckedCast<Map<String, Any>>() ?: emptyMap(),
                    enabled = m["enabled"] as? Boolean ?: true
                )
            }
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to parse triggersConfig, returning empty list" }
            emptyList()
        }
    }

    /**
     * Validate that `dagJson` is structurally well-formed and produces a cycle-free
     * DAG. Rethrows [WorkflowException] instances verbatim (they carry engine-level
     * error codes) and wraps anything else as a plain [IllegalArgumentException].
     */
    private fun validateDagJson(workflowId: String, dagJson: String) {
        try {
            val map = JsonUtil.toMap(dagJson)
            requireNotNull(map["nodes"]) { "DAG JSON must contain a 'nodes' array" }

            val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any?>>>(map["nodes"]) ?: emptyList()
            val nodes = jsonMapperHelper.parseNodesFromMap(nodesJson.uncheckedCast<List<Map<String, Any>>>() ?: emptyList())
            DagTopology.validate(workflowId, nodes)
        } catch (ex: Exception) {
            if (ex is WorkflowException) throw ex
            throw IllegalArgumentException("DAG JSON format error: ${ex.message}", ex)
        }
    }

    /**
     * Scope-aware referenced-function availability check.
     *
     * Lookup order for each `functionRef` in the DAG (matches runtime resolution):
     *   1. PLATFORM-scope DB function (globally visible) → must be ACTIVE
     *   2. Engine-level builtin registry (`builtin:` functions) → always OK
     *   3. PRIVATE-scope DB function in the same `appGroup` as the workflow → must be ACTIVE
     *   4. Back-compat fallback: any DB function matching by name alone → must be ACTIVE
     *
     * Throws IllegalArgumentException on first missing or inactive reference.
     */
    private fun validateFunctionRefs(dagJson: String, appGroup: String? = null) {
        val functionRefs = extractFunctionRefs(dagJson)
        if (functionRefs.isEmpty()) return
        functionRefs.forEach { functionName ->
            val platformFn = functionRepository.findByScopeAndFunctionName("PLATFORM", functionName).orElse(null)
            if (platformFn != null) {
                if (platformFn.status != FunctionStatus.ACTIVE) {
                    throw IllegalArgumentException("Workflow references inactive function: $functionName")
                }
                return@forEach
            }
            if (functionRegistry.contains(functionName)) return@forEach
            if (!appGroup.isNullOrBlank()) {
                val privateFn = functionRepository.findByScopeAndAppGroupAndFunctionName("PRIVATE", appGroup, functionName).orElse(null)
                if (privateFn != null) {
                    if (privateFn.status != FunctionStatus.ACTIVE) {
                        throw IllegalArgumentException("Workflow references inactive function: $functionName")
                    }
                    return@forEach
                }
            }
            val fn = functionRepository.findByFunctionName(functionName).orElse(null)
                ?: throw IllegalArgumentException("Workflow references undefined function: $functionName")
            if (fn.status != FunctionStatus.ACTIVE) {
                throw IllegalArgumentException("Workflow references inactive function: $functionName")
            }
        }
    }

    /**
     * Invoke the builtin SQL-node AOT compiler and write the resulting enriched node
     * graph back into the entity's `dagJson`. Compiler errors (missing input schema,
     * invalid SQL, etc.) surface as IllegalArgumentException to the publish/save caller.
     */
    private fun compileDbExecuteNodes(entity: WfDefinition) {
        try {
            entity.dagJson = compileDagJson(entity.dagJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Workflow DAG pre-compilation failed: ${e.message}", e)
        }
    }

    /** Pure helper: unmarshal DAG → compile → re-marshal with enriched nodes. */
    private fun compileDagJson(dagJson: String): String {
        val dag = JsonUtil.toMap(dagJson).toMutableMap()
        val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any?>>>(dag["nodes"]) ?: return dagJson
        val nodes = jsonMapperHelper.parseNodesFromMap(nodesJson.uncheckedCast<List<Map<String, Any>>>() ?: emptyList())
        val compiled = DbExecuteWorkflowCompiler.compile(WorkflowDefinition(nodes = nodes), functionRegistry)
        dag["nodes"] = compiled.nodes.map { JsonUtil.toMap(it) }
        return JsonUtil.serialize(dag)
    }

    // ===== dag_json normalisation =====

    /**
     * Sanitize `dag_json.node.type` values prior to persistence.
     *
     * Raw inputs can arrive as legacy core NodeType enums (BUILTIN/CUSTOM/SCRIPT/EXTERNAL),
     * AI-hallucinated garbage strings, or already-correct FunctionNodeType values.
     * Everything is canonicalised via [JsonMapperHelper.normalizeToFunctionNodeType] so
     * the admin UI's DAG renderer always gets a known type.
     *
     * Design principle: AI-generated inputs are untrusted. Cleanse data on write, not on read.
     */
    private fun normalizeDagJson(dagJson: String): String {
        return try {
            val dag = JsonUtil.toMap(dagJson).toMutableMap()
            val nodes = JsonUtil.convertValueOrNull<List<MutableMap<String, Any?>>>(dag["nodes"]) ?: return dagJson
            var modified = false
            nodes.forEach { node ->
                val rawType = node["type"]?.toString() ?: "CUSTOM"
                val functionRef = node["functionRef"]?.toString() ?: ""
                val normalized = jsonMapperHelper.normalizeToFunctionNodeType(rawType, functionRef)
                if (normalized != rawType) {
                    node["type"] = normalized
                    modified = true
                }
            }
            if (modified) {
                dag["nodes"] = nodes
                JsonUtil.serialize(dag)
            } else {
                dagJson
            }
        } catch (ex: Exception) {
            log.warn(ex) { "normalizeDagJson failed, keep original dagJson" }
            dagJson
        }
    }

    /**
     * Inject the default `logging:default` decorator onto every node.
     *
     * Preserves nodes that already declared decorators by prepending the default
     * at index 0 — this keeps the logging decorator first in the execution chain
     * (it needs to wrap all downstream user-defined decorators).
     */
    private fun applyDefaultLoggingDecorator(dagJson: String): String {
        return try {
            val dag = JsonUtil.toMap(dagJson).toMutableMap()
            val nodes = JsonUtil.convertValueOrNull<List<MutableMap<String, Any?>>>(dag["nodes"]) ?: return dagJson
            nodes.forEach { node ->
                val decorators = (node["decorators"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
                if (!decorators.contains("logging:default")) {
                    decorators.add(0, "logging:default")
                    node["decorators"] = decorators
                }
            }
            dag["nodes"] = nodes
            JsonUtil.serialize(dag)
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to apply default logging decorator, keep original dagJson" }
            dagJson
        }
    }

    /**
     * Extract every distinct non-blank `functionRef` value from the DAG nodes list.
     */
    private fun extractFunctionRefs(dagJson: String): Set<String> {
        return try {
            val dagData = JsonUtil.toMap(dagJson)
            val nodes = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
            nodes.mapNotNull { it["functionRef"]?.toString()?.takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (ex: Exception) {
            throw IllegalArgumentException("DAG JSON format error: ${ex.message}", ex)
        }
    }
}
