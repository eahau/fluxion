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
import com.fluxion.adapter.spi.config.DefinitionConfigPublisher
import com.fluxion.runtime.core.provider.DefinitionProvider
import com.fluxion.adapter.spi.registry.PublishTarget as SpiPublishTarget
import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.admin.route.WorkflowDefinitionRouteRefreshEvent
import com.fluxion.admin.util.toPage
import org.slf4j.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 工作流定义管理 Service
 *
 * 职责：
 *   1. CRUD 工作流定义（wf_definition 表）
 *   2. 将 DB 存储的 JSON 转换为 WorkflowDefinition 领域对象
 *   3. 缓存 ACTIVE 工作流（Caffeine 本地缓存）
 *   4. 发布工作流时通过 DefinitionConfigPublisher 推送到配置中心
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
     * 当 META 工作流发生变更时，触发 Admin 自举路由刷新。
     */
    private fun notifyRouteRefreshIfMeta(entity: WfDefinition) {
        if (entity.category == "META") {
            eventPublisher?.publishEvent(WorkflowDefinitionRouteRefreshEvent(this, entity.workflowId))
        }
    }

    /**
     * 保存或更新工作流定义
     * 若当前状态为 ACTIVE，同步推送到配置中心
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun save(entity: WfDefinition): WfDefinition {
        // 规范化 dag_json：清洗 node type 为合法的 FunctionNodeType（AI 生成的 type 可能为核心类型或未知值）
        entity.dagJson = normalizeDagJson(entity.dagJson)
        // 配置时注入默认日志装饰器，确保所有节点在持久化前已显式声明
        entity.dagJson = applyDefaultLoggingDecorator(entity.dagJson)
        // ACTIVE 状态的定义必须保证引用的函数已发布，并预编译 dbExecute 节点
        if (entity.status == "ACTIVE") {
            validateDagJson(entity.workflowId, entity.dagJson)
            validateFunctionRefs(entity.dagJson, entity.appGroup)
            compileDbExecuteNodes(entity)
        }
        val saved = repository.save(entity)
        // ACTIVE 状态的定义变更也需同步到配置中心
        if (saved.status == "ACTIVE") {
            publishToConfigCenter(saved)
        }
        notifyRouteRefreshIfMeta(saved)
        return saved
    }

    /**
     * 发布工作流（DRAFT → ACTIVE）
     * 发布前验证 DAG JSON 格式，发布后按 PublishTarget 推送到配置中心
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun publish(
        workflowId: String,
        publishTarget: com.fluxion.admin.generated.model.PublishTarget? = null
    ): WfDefinition {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: $workflowId") }
        validateDagJson(workflowId, entity.dagJson)
        // 发布前必须保证引用的函数已发布，并基于上游 schema 预编译 dbExecute 节点
        validateFunctionRefs(entity.dagJson, entity.appGroup)
        compileDbExecuteNodes(entity)
        // 发布时再次确保默认日志装饰器已注入（覆盖 dbExecute 预编译可能产生的新节点）
        entity.dagJson = applyDefaultLoggingDecorator(entity.dagJson)
        entity.status = "ACTIVE"
        entity.version += 1
        // 调用方显式传入 publishTarget 时覆盖当前值
        publishTarget?.let {
            entity.publishTarget = jsonMapperHelper.publishTargetToString(it)
        }
        val saved = repository.save(entity)
        // 推送到配置中心
        publishToConfigCenter(saved)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Published workflow [$workflowId] version [${entity.version}]" }
        return saved
    }

    /**
     * 下线工作流（ACTIVE → DEPRECATED）
     * 下线后从配置中心移除
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun deprecate(workflowId: String): WfDefinition {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: $workflowId") }
        entity.status = "DEPRECATED"
        val saved = repository.save(entity)
        // 从配置中心移除
        unpublishFromConfigCenter(workflowId)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Deprecated workflow [$workflowId]" }
        return saved
    }

    /**
     * 推送工作流定义到配置中心（支持定向发布）
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
            // 不阻断 DB 事务，降级为仅本地（下次发布可重试）
        }
    }

    /**
     * 从配置中心移除工作流定义
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
     * 根据 workflowId 获取 WorkflowDefinition 领域对象（带缓存）
     */
    @Cacheable(cacheNames = ["wfDefinition"], key = "#key")
    override fun get(key: String): WorkflowDefinition? {
        return repository.findByWorkflowId(key)
            .filter { it.status == "ACTIVE" }
            .map { toWorkflowDefinition(it) }
            .orElse(null)
    }

    /**
     * 根据 workflowId 获取原始实体（不限制状态）
     */
    fun getEntity(workflowId: String): WfDefinition? {
        return repository.findByWorkflowId(workflowId).orElse(null)
    }

    /**
     * 根据 workflowId 获取 WorkflowDefinition 领域对象（不限制状态，调试场景使用）
     */
    fun getDefinitionAnyStatus(workflowId: String): WorkflowDefinition? {
        return repository.findByWorkflowId(workflowId)
            .map { toWorkflowDefinition(it) }
            .orElse(null)
    }

    /**
     * 检查路由绑定是否已被其他工作流占用（按 scope 隔离）
     *
     * - PLATFORM scope：与全局 PLATFORM 工作流冲突
     * - PRIVATE scope：只与同 app_group 的 PRIVATE 工作流冲突
     * - MARKETPLACE scope：与全局 MARKETPLACE 工作流冲突
     *
     * @param protocol 协议（HTTP/DUBBO/GRPC/KAFKA）
     * @param method HTTP 方法（HTTP 协议时使用）
     * @param bindKey 绑定路径
     * @param scope 作用域（默认 PRIVATE）
     * @param appGroup 所属应用分组（scope=PRIVATE 时必填）
     * @param excludeWorkflowId 排除的工作流 ID（用于更新场景）
     * @return 占用该路由的工作流，无冲突返回 null
     */
    fun findRouteConflict(
        protocol: String,
        method: String?,
        bindKey: String?,
        scope: String = "PRIVATE",
        appGroup: String? = null,
        excludeWorkflowId: String? = null
    ): WfDefinition? {
        // 无绑定路径时不检查冲突
        if (bindKey.isNullOrBlank()) return null

        val conflict = if (scope == "PRIVATE") {
            // PRIVATE 只与同 app_group 的 PRIVATE 冲突
            repository.findByScopeAndAppGroupAndProtocolAndMethodAndBindKey(
                "PRIVATE", appGroup, protocol, method, bindKey
            ).orElse(null)
        } else {
            // PLATFORM / MARKETPLACE 与全局同 scope 冲突
            repository.findByScopeAndProtocolAndMethodAndBindKey(
                scope, protocol, method, bindKey
            ).orElse(null)
        }

        return conflict?.takeIf { it.workflowId != excludeWorkflowId }
    }

    /**
     * 检查路由绑定是否已被其他工作流占用（兼容旧调用方）
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
     * 分页搜索工作流定义（关键字匹配 workflowId/workflowName）
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
     * 删除工作流定义
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun delete(workflowId: String) {
        val entity = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: $workflowId") }
        repository.delete(entity)
        unpublishFromConfigCenter(workflowId)
        notifyRouteRefreshIfMeta(entity)
        log.info { "Deleted workflow [$workflowId]" }
    }

    /**
     * 回滚到指定版本（当前实现：基于最新 ACTIVE/DRAFT 复制并递增版本号）
     */
    @CacheEvict(cacheNames = ["wfDefinition", "wfBinding"], allEntries = true)
    fun rollback(workflowId: String, version: Int): WfDefinition {
        val current = repository.findByWorkflowId(workflowId)
            .orElseThrow { IllegalArgumentException("Workflow not found: $workflowId") }
        current.status = "DRAFT"
        current.version = version + 1
        val saved = repository.save(current)
        notifyRouteRefreshIfMeta(saved)
        log.info { "Rolled back workflow [$workflowId] to version [${saved.version}]" }
        return saved
    }

    /**
     * 查询某工作流的所有历史版本
     * 当前实现：按 workflowId 查询所有状态记录，按 version 升序
     */
    fun findVersions(workflowId: String): List<WfDefinition> {
        return repository.findAll()
            .filter { it.workflowId == workflowId }
            .sortedBy { it.version }
    }

    /**
     * 将工作流导出为 OpenAPI 文档（基础版）
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

    /**
     * 加载所有 ACTIVE 工作流（引擎启动时预热缓存）
     */
    fun loadAllActive(): List<WorkflowDefinition> {
        return repository.findAllActive().map { toWorkflowDefinition(it) }
    }

    /**
     * 加载所有 ACTIVE 工作流定义快照（HTTP 模式下供 Worker 拉取）
     *
     * @param appGroup 所属应用分组，传入时仅返回 PLATFORM + 该 appGroup 的 PRIVATE
     */
    fun loadAllActiveSnapshots(appGroup: String? = null): List<com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot> {
        val entities = if (appGroup.isNullOrBlank()) {
            // 兼容旧行为：返回所有 ACTIVE
            repository.findAllActive()
        } else {
            // PLATFORM + 该 appGroup 的 PRIVATE
            val platform = repository.findAllActiveByScope("PLATFORM")
            val private = repository.findAllActiveByScopeAndAppGroup("PRIVATE", appGroup)
            platform + private
        }
        return entities.map { entity ->
            com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot.of(
                entity.workflowId, entity.dagJson, entity.version, true
            )
        }
    }

    /**
     * 获取单个工作流定义快照
     */
    fun getSnapshot(workflowId: String): com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot? {
        return repository.findByWorkflowId(workflowId)
            .filter { it.status == "ACTIVE" }
            .map { entity ->
                com.fluxion.adapter.spi.config.WorkflowDefinitionSnapshot.of(
                    entity.workflowId, entity.dagJson, entity.version, true
                )
            }
            .orElse(null)
    }

    /**
     * 通过协议 + 绑定 Key 路由查找工作流 ID（带缓存）
     */
    @Cacheable(cacheNames = ["wfBinding"], key = "#protocol + ':' + #bindKey")
    override fun findByBinding(protocol: String, bindKey: String): String? {
        return repository.findByProtocolAndBindKey(protocol, bindKey)
            .filter { it.status == "ACTIVE" }
            .map { it.workflowId }
            .orElse(null)
    }

    /**
     * 将 DB 实体转换为核心领域模型 WorkflowDefinition
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
     * 解析 triggersConfig JSON 为 WorkflowTrigger 列表。
     * JSON 格式：[{"id":"t1","type":"WEBHOOK","config":{"path":"/hooks/my-workflow"},"enabled":true}]
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
            log.warn("Failed to parse triggersConfig, returning empty list", ex)
            emptyList()
        }
    }

    private fun validateDagJson(workflowId: String, dagJson: String) {
        try {
            val map = JsonUtil.toMap(dagJson)
            requireNotNull(map["nodes"]) { "DAG JSON 必须包含 'nodes' 数组" }

            val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any?>>>(map["nodes"]) ?: emptyList()
            val nodes = jsonMapperHelper.parseNodesFromMap(nodesJson.uncheckedCast<List<Map<String, Any>>>() ?: emptyList())
            DagTopology.validate(workflowId, nodes)
        } catch (ex: Exception) {
            // 保留 WorkflowException 的错误码，其它解析异常统一包装
            if (ex is WorkflowException) throw ex
            throw IllegalArgumentException("DAG JSON 格式错误: ${ex.message}", ex)
        }
    }

    /**
     * 校验 DAG 中引用的所有函数存在且已启用
     *
     * Scope-aware：PRIVATE 工作流可引用 PLATFORM 函数或同 appGroup 的 PRIVATE 函数
     */
    private fun validateFunctionRefs(dagJson: String, appGroup: String? = null) {
        val functionRefs = extractFunctionRefs(dagJson)
        if (functionRefs.isEmpty()) return
        functionRefs.forEach { functionName ->
            // 1. 先查 PLATFORM 函数（全局可用）
            val platformFn = functionRepository.findByScopeAndFunctionName("PLATFORM", functionName).orElse(null)
            if (platformFn != null) {
                if (platformFn.status != FunctionStatus.ACTIVE) {
                    throw IllegalArgumentException("工作流引用了未启用的函数: $functionName")
                }
                return@forEach
            }
            // 2. 再查 BUILTIN（代码注册）
            if (functionRegistry.contains(functionName)) return@forEach
            // 3. 查 PRIVATE 函数（同 appGroup）
            if (!appGroup.isNullOrBlank()) {
                val privateFn = functionRepository.findByScopeAndAppGroupAndFunctionName("PRIVATE", appGroup, functionName).orElse(null)
                if (privateFn != null) {
                    if (privateFn.status != FunctionStatus.ACTIVE) {
                        throw IllegalArgumentException("工作流引用了未启用的函数: $functionName")
                    }
                    return@forEach
                }
            }
            // 4. 兼容旧逻辑：直接按 functionName 查找
            val fn = functionRepository.findByFunctionName(functionName).orElse(null)
                ?: throw IllegalArgumentException("工作流引用了未定义的函数: $functionName")
            if (fn.status != FunctionStatus.ACTIVE) {
                throw IllegalArgumentException("工作流引用了未启用的函数: $functionName")
            }
        }
    }

    /**
     * 基于上游节点输出 schema 预编译 DAG 中的 dbExecute 节点，
     * 并将编译结果写回 dagJson，供运行时直接复用。
     */
    private fun compileDbExecuteNodes(entity: WfDefinition) {
        try {
            entity.dagJson = compileDagJson(entity.dagJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("工作流 DAG 预编译失败: ${e.message}", e)
        }
    }

    private fun compileDagJson(dagJson: String): String {
        val dag = JsonUtil.toMap(dagJson).toMutableMap()
        val nodesJson = JsonUtil.convertValueOrNull<List<Map<String, Any?>>>(dag["nodes"]) ?: return dagJson
        val nodes = jsonMapperHelper.parseNodesFromMap(nodesJson.uncheckedCast<List<Map<String, Any>>>() ?: emptyList())
        val compiled = DbExecuteWorkflowCompiler.compile(WorkflowDefinition(nodes = nodes), functionRegistry)
        dag["nodes"] = compiled.nodes.map { JsonUtil.toMap(it) }
        return JsonUtil.serialize(dag)
    }

    // ─── dag_json 规范化 ──────────────────────────────────────────────

    /**
     * 规范化 dag_json 中的 node type 字段：
     * 无论输入是核心 NodeType（BUILTIN/CUSTOM/SCRIPT/EXTERNAL）、未知类型、还是 FunctionNodeType，
     * 统一清洗为合法的 FunctionNodeType（PARAM_VALIDATE/DATA_QUERY/CUSTOM 等），确保前端可正确渲染。
     *
     * 设计原则：AI 生成的数据不可信，在持久化前统一清洗，而非在读取时补救。
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
     * 配置时为所有节点注入默认日志装饰器 logging:default，
     * 若节点已显式声明则去重，保持 logging:default 在装饰器链首位。
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
     * 从 DAG JSON 中提取所有 functionRef
     */
    private fun extractFunctionRefs(dagJson: String): Set<String> {
        return try {
            val dagData = JsonUtil.toMap(dagJson)
            val nodes = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(dagData["nodes"]) ?: emptyList()
            nodes.mapNotNull { it["functionRef"]?.toString()?.takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (ex: Exception) {
            throw IllegalArgumentException("DAG JSON 格式错误: ${ex.message}", ex)
        }
    }
}
