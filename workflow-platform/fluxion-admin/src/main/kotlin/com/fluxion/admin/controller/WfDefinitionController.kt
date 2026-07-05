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
 * 工作流定义管理 REST API
 *
 * 实现 OpenAPI 生成的 WorkflowsApi 接口，确保前后端契约一致。
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
        // 租户过滤：非 ADMIN 用户只能看到 PLATFORM + 自己 appGroup 的 PRIVATE
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

    override fun getWorkflow(workflowId: String): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(entity))
    }

    override fun createWorkflow(workflowDefinition: WorkflowDefinition): ResponseEntity<WorkflowDefinition> {
        val entity = workflowMapper.toEntity(workflowDefinition)
        if (entity.workflowId.isBlank()) {
            entity.workflowId = generateWorkflowId(entity)
        }
        check(service.getEntity(entity.workflowId) == null) { "工作流已存在: ${entity.workflowId}" }
        // PRIVATE scope 必须指定 appGroup
        if (entity.scope == "PRIVATE" && entity.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE 作用域的工作流必须指定 appGroup")
        }
        // 租户权限校验
        entity.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        // 检查路由绑定是否冲突（相同协议 + 方法 + 路径，按 scope 隔离）
        service.findRouteConflict(
            protocol = entity.protocol,
            method = entity.method,
            bindKey = entity.bindKey,
            scope = entity.scope,
            appGroup = entity.appGroup
        )?.let { conflict ->
            throw WorkflowAdminException.badRequest(
                "ROUTE_CONFLICT",
                "路由已被工作流 [${conflict.workflowId}] 占用: ${entity.protocol} ${entity.method ?: ""} ${entity.bindKey ?: ""}"
            )
        }
        return ResponseEntity.ok(workflowMapper.toDto(service.save(entity)))
    }

    /**
     * 根据协议绑定信息自动生成可读的 workflowId。
     * HTTP/HTTPS 协议：{protocol}-{slugified-path}（HTTPS 归一化为 http）；其他协议：wf-{uuid8}
     * 若已存在同名工作流，追加数字后缀。
     */
    private fun generateWorkflowId(entity: com.fluxion.admin.entity.WfDefinition): String {
        val base = if (entity.protocol in HTTP_PROTOCOLS && !entity.bindKey.isNullOrBlank()) {
            val proto = "http" // HTTP/HTTPS 归一化
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

    override fun updateWorkflow(
        workflowId: String,
        workflowDefinition: WorkflowDefinition
    ): ResponseEntity<WorkflowDefinition> {
        val existing = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        // 租户权限校验（校验原始 appGroup）
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        workflowMapper.updateEntity(workflowDefinition, existing)
        // PRIVATE scope 必须指定 appGroup
        if (existing.scope == "PRIVATE" && existing.appGroup.isNullOrBlank()) {
            throw WorkflowAdminException.badRequest("SCOPE_APP_GROUP_REQUIRED", "PRIVATE 作用域的工作流必须指定 appGroup")
        }
        // 若 appGroup 变更，还需校验新 appGroup 的权限
        existing.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        // 检查路由绑定是否与其他工作流冲突（按 scope 隔离）
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
                "路由已被工作流 [${conflict.workflowId}] 占用: ${existing.protocol} ${existing.method ?: ""} ${existing.bindKey ?: ""}"
            )
        }
        return ResponseEntity.ok(workflowMapper.toDto(service.save(existing)))
    }

    override fun deleteWorkflow(workflowId: String): ResponseEntity<Unit> {
        val existing = service.getEntity(workflowId)
        existing?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.delete(workflowId)
        return ResponseEntity.noContent().build()
    }

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
                    finalOutput = mutableMapOf("error" to (ex.message ?: "执行失败"))
                }
            )
        }

        // 同步保存执行快照（管理端执行默认记录）
        snapshotService.save(result, entity.workflowId, entity.version)

        return ResponseEntity.ok(toDebugResult(result))
    }

    override fun publishWorkflow(
        workflowId: String,
        publishTarget: PublishTarget?
    ): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.publish(workflowId, publishTarget)))
    }

    override fun deprecateWorkflow(workflowId: String): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.deprecate(workflowId)))
    }

    override fun listWorkflowVersions(workflowId: String): ResponseEntity<List<WorkflowDefinition>> {
        val versions = service.findVersions(workflowId)
        return ResponseEntity.ok(versions.map { workflowMapper.toDto(it) })
    }

    override fun rollbackWorkflow(
        workflowId: String,
        rollbackRequest: RollbackRequest
    ): ResponseEntity<WorkflowDefinition> {
        val entity = service.getEntity(workflowId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(workflowMapper.toDto(service.rollback(workflowId, rollbackRequest.version)))
    }

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
        val now = OffsetDateTime.now()
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
                            id = operationId
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
     * 导入工作流定义 JSON 文件（非 OpenAPI 生成端点）
     */
    @PostMapping("/api/admin/workflows/import/definition", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importWorkflowDefinition(
        @RequestParam("file") file: MultipartFile?
    ): ResponseEntity<WorkflowDefinition> {
        if (file == null || file.isEmpty) {
            throw WorkflowAdminException.badRequest("IMPORT_FILE_EMPTY", "导入文件不能为空")
        }
        val content = file.inputStream.bufferedReader().use { it.readText() }
        val dto = try {
            JsonUtil.deserialize(content, WorkflowDefinition::class.java)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_JSON_INVALID", "JSON 解析失败: ${ex.message}")
        }
        // 导入时重置为草稿，避免直接覆盖 ACTIVE 状态导致配置中心异常
        dto.status = WorkflowStatus.DRAFT
        dto.version = 1
        val now = OffsetDateTime.now()
        dto.createdAt = now
        dto.updatedAt = now
        val entity = try {
            workflowMapper.toEntity(dto)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_CONVERT_ERROR", "工作流转换失败: ${ex.message}")
        }
        val saved = try {
            service.save(entity)
        } catch (ex: Exception) {
            throw WorkflowAdminException.badRequest("IMPORT_SAVE_ERROR", "保存失败: ${ex.message}")
        }
        return ResponseEntity.ok(workflowMapper.toDto(saved))
    }

    override fun confirmImportOpenAPI(
        importConfirmRequest: ImportConfirmRequest
    ): ResponseEntity<List<WorkflowDefinition>> {
        val items = importConfirmRequest.items ?: emptyList()
        // 预检查：收集已存在的 ID 和路由冲突
        val duplicateIds = mutableListOf<String>()
        val routeConflicts = mutableListOf<String>()
        items.forEach { dto ->
            val id = dto.id
            if (!id.isNullOrBlank() && service.getEntity(id) != null) {
                duplicateIds.add(id)
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
                        excludeWorkflowId = id
                    )?.let { conflict ->
                            routeConflicts.add("${entity.protocol} ${entity.method ?: ""} ${entity.bindKey} → 已被 [${conflict.workflowId}] 占用")
                        }
                }
            } catch (_: Exception) {
                // 转换失败的在后续保存时也会失败，由下面统一处理
            }
        }
        val errors = mutableListOf<String>()
        if (duplicateIds.isNotEmpty()) errors.add("工作流 ID 已存在: ${duplicateIds.joinToString(", ")}")
        if (routeConflicts.isNotEmpty()) errors.add("路由冲突: ${routeConflicts.joinToString("; ")}")
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

    override fun exportOpenAPI(
        ids: List<String>?,
        title: String?,
        version: String?
    ): ResponseEntity<Any> {
        val exported = service.exportOpenAPI(ids ?: emptyList(), title, version)
        return ResponseEntity.ok(exported)
    }

    override fun batchExportOpenAPI(openAPIExportRequest: OpenAPIExportRequest): ResponseEntity<Any> {
        val exported = service.exportOpenAPI(openAPIExportRequest.ids, openAPIExportRequest.title, openAPIExportRequest.version)
        return ResponseEntity.ok(exported)
    }

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
