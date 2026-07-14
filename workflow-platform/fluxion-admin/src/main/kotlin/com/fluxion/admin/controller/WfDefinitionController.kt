package com.fluxion.admin.controller

import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.admin.exception.WorkflowAdminException
import com.fluxion.core.engine.DagExecutor
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.NodeExecutionRecord
import com.fluxion.admin.generated.api.WorkflowsApi
import com.fluxion.admin.generated.model.DebugResult
import com.fluxion.admin.generated.model.DebugStatus
import com.fluxion.admin.generated.model.ExecuteWorkflowRequest
import com.fluxion.admin.generated.model.ImportConfirmRequest
import com.fluxion.admin.generated.model.NodeTrace
import com.fluxion.admin.generated.model.NodeTraceStatus
import com.fluxion.admin.generated.model.OpenAPIExportRequest
import com.fluxion.admin.generated.model.PageResponseWorkflowDefinition
import com.fluxion.admin.generated.model.PublishTarget
import com.fluxion.admin.generated.model.RollbackRequest
import com.fluxion.admin.generated.model.WorkflowCategory
import com.fluxion.admin.generated.model.WorkflowDefinition
import com.fluxion.admin.generated.model.WorkflowStatus
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.mapper.WorkflowMapper
import com.fluxion.admin.service.WfDefinitionService
import com.fluxion.admin.service.WfExecutionSnapshotService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.yaml.snakeyaml.Yaml
import java.time.OffsetDateTime

/**
 * Workflow definition administration & execution REST controller (implements OpenAPI-generated [WorkflowsApi]).
 *
 * Bundles three families of operations:
 *   1. **Admin CRUD + lifecycle** — create/update/delete/search, publish / deprecate / rollback,
 *      route-conflict pre-checks, tenant filtering for list pages.
 *   2. **Synchronous test execution** — `executeWorkflow` runs the DAG inside the admin
 *      JVM (via [DagExecutor]) and persists a snapshot for the debug UI.
 *   3. **OpenAPI tooling** — import from OpenAPI YAML/JSON + confirm import (bulk conflict
 *      detection) + export selected workflows back to OpenAPI 3.0.
 *
 * Collaborates with: WfDefinitionService (business logic + config push), WorkflowMapper
 * (DTO↔Entity + DAG node graph mapping), DagExecutor (sync test execution),
 * DefinitionProvider (only ACTIVE definitions are executable), WfExecutionSnapshotService
 * (snapshot persistence), SecurityContextHelper (tenant filtering for list pages).
 */
@RestController
class WfDefinitionController(
    private val service: WfDefinitionService,
    private val workflowMapper: WorkflowMapper,
    private val dagExecutor: DagExecutor,
    private val definitionProvider: DefinitionProvider,
    private val snapshotService: WfExecutionSnapshotService,
    private val securityContext: SecurityContextHelper
) : WorkflowsApi {

    /**
     * Paginated workflow list with optional keyword filter.
     *
     * Post-pagination tenant filter (re-applied in-memory): non-ADMIN users see only
     * PLATFORM + MARKETPLACE rows plus PRIVATE rows whose appGroup they belong to.
     * ADMIN users (accessibleGroups == null) see every row unfiltered.
     */
    override fun listWorkflows(
        keyword: String?,
        status: WorkflowStatus?,
        category: WorkflowCategory?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseWorkflowDefinition> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        var result = service.search(keyword, pageable)
        val accessibleGroups = securityContext.accessibleAppGroups()
        if (accessibleGroups != null) {
            val filtered = result.content.filter { entity ->
                entity.scope == "PLATFORM" || entity.scope == "MARKETPLACE" ||
                    (entity.scope == "PRIVATE" && entity.appGroup in accessibleGroups)
            }
            result = org.springframework.data.domain.PageImpl(filtered, pageable, filtered.size.toLong())
        }
        return ResponseEntity.ok(PageResponseWorkflowDefinition().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { workflowMapper.toDto(it) }.toMutableList()
        })
    }

    /** Fetch one workflow definition by business id. */
    override fun getWorkflow(workflowId: String): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(entity))
    }

    /**
     * Create a new workflow definition.
     *
     * Guards (in order):
     *   - Auto-generates workflowId from HTTP path slug if blank (see [generateWorkflowId])
     *   - 409 if id already exists
     *   - PRIVATE scope requires non-blank appGroup
     *   - appGroup tenant access check
     *   - route-conflict pre-flight against protocol+method+bindKey (scope isolated)
     */
    override fun createWorkflow(workflowDefinition: WorkflowDefinition): ResponseEntity<WorkflowDefinition> {
        val entity = workflowMapper.toEntity(workflowDefinition)
        if (entity.workflowId.isBlank()) {
            entity.workflowId = generateWorkflowId(entity)
        }
        check(service.getEntity(entity.workflowId) == null) { "Workflow already exists: ${entity.workflowId}" }
        if (entity.scope == "PRIVATE" && entity.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE scope workflows require appGroup")
        }
        entity.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.findRouteConflict(
            protocol = entity.protocol,
            method = entity.method,
            bindKey = entity.bindKey,
            scope = entity.scope,
            appGroup = entity.appGroup
        )?.let { conflict ->
            throw WorkflowAdminException.badRequest(
                "ROUTE_CONFLICT",
                "Route already occupied by workflow [${conflict.workflowId}]: ${entity.protocol} ${entity.method ?: ""} ${entity.bindKey ?: ""}"
            )
        }
        return ResponseEntity.ok(workflowMapper.toDto(service.save(entity)))
    }

    /**
     * Build a human-friendly workflow id.
     *
     * HTTP/HTTPS protocols: `http-{slugified-bindKey}` where slashes and non-alnum chars
     * become dashes. Everything else: `wf-{uuid8}`. Collision-safe: if the candidate id
     * is already taken, append `-1`, `-2`, ... until a free slot is found.
     */
    private fun generateWorkflowId(entity: com.fluxion.admin.entity.WfDefinition): String {
        val base = if (entity.protocol in HTTP_PROTOCOLS && !entity.bindKey.isNullOrBlank()) {
            val proto = "http"
            val slug = entity.bindKey!!
                .trimStart('/')
                .replace(Regex("[^a-zA-Z0-9]+"), "-")
                .trimEnd('-')
                .lowercase()
            "$proto-$slug"
        } else {
            "wf-${java.util.UUID.randomUUID().toString().take(8)}"
        }
        var id = base
        var suffix = 1
        while (service.getEntity(id) != null) {
            id = "$base-$suffix"
            suffix++
        }
        return id
    }

    /**
     * Update a workflow definition. Mirrors the same guards used during create with
     * the addition of `excludeWorkflowId = self` so the route-conflict check ignores
     * the workflow's own existing binding.
     */
    override fun updateWorkflow(
        workflowId: String,
        workflowDefinition: WorkflowDefinition
    ): ResponseEntity<WorkflowDefinition> {
        val existing = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        workflowMapper.updateEntity(workflowDefinition, existing)
        if (existing.scope == "PRIVATE" && existing.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE scope workflows require appGroup")
        }
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.findRouteConflict(
            protocol = existing.protocol,
            method = existing.method,
            bindKey = existing.bindKey,
            scope = existing.scope,
            appGroup = existing.appGroup,
            excludeWorkflowId = workflowId
        )?.let { conflict ->
            throw WorkflowAdminException.badRequest(
                "ROUTE_CONFLICT",
                "Route already occupied by workflow [${conflict.workflowId}]: ${existing.protocol} ${existing.method ?: ""} ${existing.bindKey ?: ""}"
            )
        }
        return ResponseEntity.ok(workflowMapper.toDto(service.save(existing)))
    }

    /** Delete a workflow definition (tenant-gated by existing appGroup). */
    override fun deleteWorkflow(workflowId: String): ResponseEntity<Unit> {
        val existing = service.getEntity(workflowId)
        existing?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.delete(workflowId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Execute a workflow synchronously inside the admin JVM (debug / test-run button).
     *
     * Requires the workflow to be ACTIVE (uses definitionProvider so non-ACTIVE versions
     * correctly 404). Exceptions during execution are caught and returned as a FAILED
     * DebugResult rather than 500ing — the UI renders the inline error message. Snapshot
     * is persisted regardless of success so the debug UI can inspect partial outputs.
     */
    override fun executeWorkflow(
        workflowId: String,
        executeWorkflowRequest: ExecuteWorkflowRequest?
    ): ResponseEntity<DebugResult> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()

        val input = executeWorkflowRequest?.inputs ?: emptyMap()
        val result = try {
            val definition = definitionProvider.get(workflowId)
                ?: return ResponseEntity.notFound().build()
            kotlinx.coroutines.runBlocking {
                dagExecutor.execute(definition, input)
            }
        } catch (ex: Exception) {
            return ResponseEntity.ok(
                DebugResult().apply {
                    status = DebugStatus.FAILED
                    finalOutput = mutableMapOf("error" to (ex.message ?: "Execution failed"))
                }
            )
        }

        snapshotService.save(result, entity.workflowId, entity.version)

        return ResponseEntity.ok(toDebugResult(result))
    }

    /** Publish a workflow (DRAFT → ACTIVE) with optional target-group directives. */
    override fun publishWorkflow(
        workflowId: String,
        publishTarget: PublishTarget?
    ): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.publish(workflowId, publishTarget)))
    }

    /** Deprecate a workflow (ACTIVE → DEPRECATED). */
    override fun deprecateWorkflow(workflowId: String): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.deprecate(workflowId)))
    }

    /** List historical versions for a workflow (currently one row). */
    override fun listWorkflowVersions(workflowId: String): ResponseEntity<List<WorkflowDefinition>> {
        val versions = service.findVersions(workflowId)
        return ResponseEntity.ok(versions.map { workflowMapper.toDto(it) })
    }

    /** Rollback (placeholder: flips latest row back to DRAFT + bumps version). */
    override fun rollbackWorkflow(
        workflowId: String,
        rollbackRequest: RollbackRequest
    ): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.rollback(workflowId, rollbackRequest.version)))
    }

    /**
     * Preview-import an OpenAPI 3.0 document (YAML or JSON). Returns a list of
     * unsaved WorkflowDefinition DTOs — one per HTTP method/path combination — so
     * the UI can render the "confirm import" diff modal.
     */
    override fun importOpenAPI(@RequestPart(value = "file", required = false) file: MultipartFile?): ResponseEntity<List<WorkflowDefinition>> {
        if (file == null || file.isEmpty) {
            return ResponseEntity.ok(emptyList())
        }
        val content = file.inputStream.bufferedReader().use { it.readText() }
        val doc = try {
            val raw: Any? = Yaml().load(content)
            raw.uncheckedCast<Map<String, Any>>()
        } catch (_: Exception) {
            JsonUtil.toMap(content)
        } ?: return ResponseEntity.ok(emptyList())

        val paths = doc["paths"].uncheckedCast<Map<String, Map<String, Any>>>() ?: emptyMap()
        val now = OffsetDateTime.now().toInstant().toEpochMilli()
        val imported = mutableListOf<WorkflowDefinition>()

        paths.forEach { (path, methods) ->
            methods.filterKeys { it.lowercase() in HTTP_METHODS }
                .forEach { (method, opAny) ->
                    val op = opAny.uncheckedCast<Map<String, Any>>() ?: return@forEach
                    val operationId = op["operationId"]?.toString() ?: run {
                        val slug = path.trimStart('/').replace(Regex("[^a-zA-Z0-9]+"), "-").trimEnd('-').lowercase()
                        "http-$slug"
                    }
                    val summary = op["summary"]?.toString() ?: operationId

                    val inputSchema = extractSchema(op, listOf("requestBody", "content", "application/json", "schema"))
                    val outputSchema = extractSchema(op, listOf("responses", "200", "content", "application/json", "schema"))

                    imported.add(
                        WorkflowDefinition(
                            name = summary,
                            category = WorkflowCategory.BUSINESS,
                            protocol = "HTTP",
                            nodes = mutableListOf()
                        ).apply {
                            workflowId = operationId
                            this.method = method.uppercase()
                            this.path = path
                            status = WorkflowStatus.DRAFT
                            this.inputSchema = inputSchema?.toMutableMap() ?: mutableMapOf()
                            this.outputSchema = outputSchema?.toMutableMap() ?: mutableMapOf()
                            transactionConfig = mutableMapOf()
                            version = 1
                            isProtected = false
                            createdAt = now
                            updatedAt = now
                        }
                    )
                }
        }
        return ResponseEntity.ok(imported)
    }

    /**
     * Import a single workflow-definition JSON file (non-OpenAPI format; raw persisted
     * DTO export). Resets status → DRAFT + version → 1 so it can't silently override
     * a live ACTIVE definition in the config center.
     */
    @PostMapping("/api/admin/workflows/import/definition", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importWorkflowDefinition(
        @RequestParam("file") file: MultipartFile?
    ): ResponseEntity<WorkflowDefinition> {
        if (file == null || file.isEmpty) {
            throw WorkflowAdminException.badRequest("IMPORT_FILE_EMPTY", "Import file cannot be empty")
        }
        val content = file.inputStream.bufferedReader().use { it.readText() }
        val dto = try {
            JsonUtil.deserialize(content, WorkflowDefinition::class.java)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_JSON_INVALID", "JSON parse failed: ${ex.message}")
        }
        dto.status = WorkflowStatus.DRAFT
        dto.version = 1
        val now = OffsetDateTime.now().toInstant().toEpochMilli()
        dto.createdAt = now
        dto.updatedAt = now
        val entity = try {
            workflowMapper.toEntity(dto)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_CONVERT_ERROR", "Workflow conversion failed: ${ex.message}")
        }
        val saved = try {
            service.save(entity)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_SAVE_ERROR", "Save failed: ${ex.message}")
        }
        return ResponseEntity.ok(workflowMapper.toDto(saved))
    }

    /**
     * Confirm bulk OpenAPI import. Runs two pre-checks before saving:
     *   1. Collision check (duplicate ids across the batch)
     *   2. Route-conflict pre-flight for every row that declares a bindKey
     *
     * Any failure throws a single combined 409. Successes are saved in order; any row
     * whose conversion fails mid-batch is simply skipped (silent skip keeps the UI
     * responsive when one row in a large batch is badly-formed).
     */
    override fun confirmImportOpenAPI(
        importConfirmRequest: ImportConfirmRequest
    ): ResponseEntity<List<WorkflowDefinition>> {
        val items = importConfirmRequest.items ?: emptyList()
        val duplicateIds = mutableListOf<String>()
        val routeConflicts = mutableListOf<String>()
        items.forEach { dto ->
            val id = dto.id
            if (id != null && service.getEntityById(id) != null) {
                duplicateIds.add(id.toString())
            }
            try {
                val entity = workflowMapper.toEntity(dto)
                if (entity.bindKey?.isNotBlank() == true) {
                    service.findRouteConflict(
                        protocol = entity.protocol,
                        method = entity.method,
                        bindKey = entity.bindKey,
                        scope = entity.scope,
                        appGroup = entity.appGroup,
                        excludeWorkflowId = dto.workflowId
                    )?.let { conflict ->
                            routeConflicts.add("${entity.protocol} ${entity.method ?: ""} ${entity.bindKey} → occupied by [${conflict.workflowId}]")
                        }
                }
            } catch (_: Exception) {
            }
        }
        val errors = mutableListOf<String>()
        if (duplicateIds.isNotEmpty()) errors.add("Duplicate workflow IDs: ${duplicateIds.joinToString(", ")}")
        if (routeConflicts.isNotEmpty()) errors.add("Route conflicts: ${routeConflicts.joinToString("; ")}")
        if (errors.isNotEmpty()) {
            throw WorkflowAdminException.conflict("IMPORT_CONFLICT", errors.joinToString("\n"))
        }
        val saved = items.mapNotNull { dto ->
            try {
                val entity = workflowMapper.toEntity(dto)
                workflowMapper.toDto(service.save(entity))
            } catch (_: Exception) {
                null
            }
        }
        return ResponseEntity.ok(saved)
    }

    /** Export a subset of workflow IDs (or all ACTIVE) to an OpenAPI 3.0 map. */
    override fun exportOpenAPI(
        ids: List<String>?,
        title: String?,
        version: String?
    ): ResponseEntity<Any> {
        val exported = service.exportOpenAPI(ids ?: emptyList(), title, version)
        return ResponseEntity.ok(exported)
    }

    /** Batch variant of the export endpoint (POST body carries the id list). */
    override fun batchExportOpenAPI(openAPIExportRequest: OpenAPIExportRequest): ResponseEntity<Any> {
        val exported = service.exportOpenAPI(openAPIExportRequest.ids, openAPIExportRequest.title, openAPIExportRequest.version)
        return ResponseEntity.ok(exported)
    }

    /** EngineResult → DebugResult DTO mapping. */
    private fun toDebugResult(result: EngineResult): DebugResult =
        DebugResult().apply {
            status = if (result.success) DebugStatus.COMPLETED else DebugStatus.FAILED
            finalOutput = result.data.uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
            totalDurationMs = result.trace.sumOf { it.durationMs }.toInt()
            traces = result.trace.map { toNodeTrace(it) }.toMutableList()
            snapshot = result.finalState?.let { state ->
                mutableMapOf<String, Any>(
                    "executionId" to (state.meta.executionId ?: ""),
                    "inputs" to state.inputs,
                    "nodeOutputs" to state.nodeOutputs
                )
            } ?: mutableMapOf()
        }

    /** NodeExecutionRecord → NodeTrace DTO mapping (UI renderable trace node). */
    private fun toNodeTrace(record: NodeExecutionRecord): NodeTrace =
        NodeTrace().apply {
            nodeId = record.nodeId
            nodeName = record.nodeName
            status = NodeTraceStatus.forValue(record.status.name)
            durationMs = record.durationMs.toInt()
            val rawInput = record.input.uncheckedCast<Map<String, Any>>()
            inputs = if (rawInput != null) mutableMapOf(record.nodeId to rawInput) else mutableMapOf()
            output = record.output.uncheckedCast<Map<String, Any>>()?.toMutableMap() ?: mutableMapOf()
            error = record.errorMessage
        }

    /** Walk a nested OpenAPI JSON map extracting a schema document (returns null if any segment missing). */
    private fun extractSchema(op: Map<String, Any>, path: List<String>): Map<String, Any>? {
        var current: Any? = op
        for (key in path) {
            current = current.uncheckedCast<Map<String, Any>>()?.get(key)
            if (current == null) return null
        }
        return current.uncheckedCast<Map<String, Any>>()
    }

    companion object {
        private val HTTP_METHODS = setOf("get", "post", "put", "delete", "patch", "head", "options", "trace")
        private val HTTP_PROTOCOLS = setOf("HTTP", "HTTPS")
    }
}
