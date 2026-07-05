package com.fluxion.admin.service

import com.fluxion.adapter.spi.config.SchemaConfigPublisher
import com.fluxion.adapter.spi.config.SchemaConfigSnapshot
import com.fluxion.admin.entity.WfSchema
import com.fluxion.admin.entity.WfSchemaSummary
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfSchemaRepository
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.util.toPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Schema 定义管理 Service
 *
 * 职责：
 *   1. CRUD Schema 定义（wf_schema 表）
 *   2. 通过 SchemaConfigPublisher 将 Schema 变更推送到 Worker 实例
 *   3. 提供全量/单条 Schema 快照供 Worker 拉取
 */
@Service
class WfSchemaService(
    private val repository: WfSchemaRepository,
    private val definitionRepository: WfDefinitionRepository,
    private val securityContext: SecurityContextHelper,
    private val schemaConfigPublisher: SchemaConfigPublisher? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** 解锁/修改被冻结 Schema 所需的权限 */
        const val SCHEMA_UNLOCK_PERMISSION = "schema:unlock"

        /** 编辑 Schema 所需的权限 */
        const val SCHEMA_EDIT_PERMISSION = "schema:edit"

        /** Schema 引用前缀（JSON Schema $ref 中使用 schema:<name> 引用已注册 Schema） */
        const val SCHEMA_REF_PREFIX = "schema:"

        /** Schema 引用正则：匹配 $ref: "schema:<name>" 格式（要求前缀前是 " ，避免误匹配 json-schema: 前缀） */
        private val SCHEMA_REF_REGEX = Regex("\"\\\$ref\"\\s*:\\s*\"schema:([^\"]+)\"")

        /** 判断某 JSON 内容里是否包含对指定 schemaName 的精确引用（排除 json-schema: 前缀造成的误匹配） */
        private fun containsReferenceTo(schemaJson: String, schemaName: String): Boolean {
            return SCHEMA_REF_REGEX.findAll(schemaJson)
                .any { it.groupValues[1] == schemaName }
        }
    }

    /**
     * 判断当前登录用户是否具备指定权限。
     *
     * 与 SecurityContextHelper.isAdmin() 保持完全一致：
     *   - 未认证 / 匿名认证 = local/dev 免鉴权环境 = ADMIN = 拥有所有权限
     *   - 已认证用户：具体权限命中，或 authorities 中包含 ROLE_ADMIN / ADMIN 任意一种
     *
     * 这样避免「前端看到冻结 Switch（前端 canUnlock=true）但后端实际 canUnlock=false
     * 导致 frozen=true 提交后又被复原为 false」的权限判断不一致问题。
     */
    private fun hasPermission(permission: String): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return true
        if (auth is AnonymousAuthenticationToken) return true
        val authorities = auth.authorities?.map { it.authority } ?: emptyList()
        return authorities.contains(permission) ||
               authorities.contains("ROLE_ADMIN") ||
               authorities.contains("ADMIN")
    }

    fun search(keyword: String?, schemaType: String?, pageable: Pageable): Page<WfSchema> {
        return searchSummaries(keyword, schemaType, pageable)
            .map { s ->
                WfSchema().apply {
                    id = s.id
                    this.schemaName = s.schemaName
                    this.schemaType = s.schemaType
                    this.schemaFormat = s.schemaFormat
                    this.description = s.description
                    this.frozen = s.frozen
                    this.scope = s.scope
                    this.appGroup = s.appGroup
                    this.createdAt = s.createdAt
                    this.updatedAt = s.updatedAt
                }
            }
    }

    /**
     * 列表页专用的轻量搜索方法。
     *
     * 优化点（对应 TTFB 1.21s + 下载 279ms 的两个爆炸点）：
     * 1. 只用 JPQL SELECT NEW 查轻量 DTO [WfSchemaSummary]，不查大 TEXT 字段 schemaJson
     *    → JDBC 网络 I/O 从 2MB 降到 ~20KB，TTFB 至少快 100x
     * 2. keyword / scope 条件下推 SQL，不再 repository.findAll() 全表加载
     * 3. schemaType 多值精确匹配（逗号分隔 split）保留在内存，轻量 DTO 即使 1000 条也仅 ~200KB
     * 4. 最后内存分页（保持与旧逻辑完全一致的分页结果）
     *
     * @return 轻量投影 Page，不包含 schemaJson 大字段，下游配合 [SchemaMapper.toDto(WfSchemaSummary)] 用
     */
    fun searchSummaries(keyword: String?, schemaType: String?, pageable: Pageable): Page<WfSchemaSummary> {
        val accessibleGroups = securityContext.accessibleAppGroups()

        val (scopeFilter, appGroups) = when {
            accessibleGroups == null -> 2 to emptyList<String>()   // 免鉴权 / ADMIN：scope 全不过滤
            accessibleGroups.isEmpty() -> 0 to emptyList()        // 无分组权限：仅 PLATFORM
            else -> 1 to accessibleGroups                         // 普通用户：PLATFORM + 自己的 PRIVATE 分组
        }

        // ① DB 层：轻量查询 + keyword/scope 下推（不查 schemaJson，不做分页）
        var all = repository.listSummaries(keyword, scopeFilter, appGroups)

        // ② 内存层：schemaType 精确匹配（多值逗号分隔，JPQL 无法准确表达）
        if (!schemaType.isNullOrBlank()) {
            val target = schemaType.uppercase()
            all = all.filter { entity ->
                entity.schemaType.split(',').map { it.trim() }.contains(target)
            }
        }

        // ③ 内存分页（保持与旧 API 完全一致的 Page 语义）
        return all.toPage(pageable)
    }

    fun getByName(schemaName: String): WfSchema? {
        return repository.findBySchemaName(schemaName).orElse(null)
    }

    fun save(entity: WfSchema): WfSchema {
        // 创建前校验所有 schema 引用是否合法（不允许自引用，因为自身尚未保存）
        validateSchemaReferences(entity.schemaJson, null)
        val saved = repository.save(entity)
        log.info("Saved schema [{}]", saved.schemaName)
        publishToWorkers(saved)
        return saved
    }

    @Transactional
    fun update(schemaName: String, entity: WfSchema): WfSchema {
        val existing = repository.findBySchemaName(schemaName)
            .orElseThrow { IllegalArgumentException("Schema not found: $schemaName") }

        val canUnlock = hasPermission(SCHEMA_UNLOCK_PERMISSION)
        if (existing.frozen && !canUnlock) {
            throw AccessDeniedException("Schema [$schemaName] 已冻结，当前用户无权限修改")
        }

        // 更新前校验 schema 引用是否合法（允许自引用）
        validateSchemaReferences(entity.schemaJson, schemaName)

        existing.schemaType = entity.schemaType
        existing.schemaFormat = entity.schemaFormat
        existing.schemaJson = entity.schemaJson
        existing.description = entity.description
        existing.scope = entity.scope
        existing.appGroup = entity.appGroup
        // 仅具备 schema:unlock 权限的用户才能变更冻结状态
        if (canUnlock) {
            existing.frozen = entity.frozen
        }

        val saved = repository.save(existing)
        log.info("Updated schema [{}], frozen={}", schemaName, saved.frozen)
        publishToWorkers(saved)
        return saved
    }

    @Transactional
    fun delete(schemaName: String) {
        val entity = repository.findBySchemaName(schemaName)
            .orElseThrow { IllegalArgumentException("Schema 不存在: $schemaName") }

        if (entity.frozen && !hasPermission(SCHEMA_UNLOCK_PERMISSION)) {
            throw AccessDeniedException("Schema [$schemaName] 已冻结，当前用户无权限删除")
        }

        // 检查是否被其他 Schema 或工作流定义引用
        val referrers = findReferrers(schemaName)
        if (referrers.isNotEmpty()) {
            throw IllegalArgumentException(
                "Schema [$schemaName] 正在被引用，无法删除。引用来源: ${referrers.joinToString(", ")}"
            )
        }

        repository.delete(entity)
        log.info("Deleted schema [{}]", schemaName)
        unpublishFromWorkers(schemaName)
    }

    /**
     * 从 Schema JSON 内容中提取所有被引用的 Schema 名称。
     */
    fun extractReferencedSchemaNames(schemaJson: String): Set<String> {
        return SCHEMA_REF_REGEX.findAll(schemaJson)
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * 校验 Schema 中所有引用的目标 Schema 是否都存在。
     * @throws IllegalArgumentException 如果存在引用了不存在的 Schema
     */
    fun validateSchemaReferences(schemaJson: String, selfName: String? = null) {
        val referenced = extractReferencedSchemaNames(schemaJson)
        if (referenced.isEmpty()) return

        val allExistingNames = repository.findAll().map { it.schemaName }.toSet()
        val invalidRefs = referenced.filter { ref ->
            ref != selfName && !allExistingNames.contains(ref)
        }
        if (invalidRefs.isNotEmpty()) {
            throw IllegalArgumentException(
                "Schema 引用了不存在的目标 Schema：${invalidRefs.joinToString(", ")}。" +
                    "请先创建这些 Schema，或修正引用关系。"
            )
        }
    }

    /**
     * 查找引用了指定 Schema 的所有来源。
     *
     * 引用可能出现在：
     * 1. 其他 Schema 的 schemaJson 中（通过 `$ref: "schema:<name>"` 引用）
     * 2. 工作流定义的 inputSchema / outputSchema 中（通过 `$ref: "schema:<name>"` 引用）
     */
    private fun findReferrers(schemaName: String): List<String> {
        val referrers = mutableListOf<String>()

        // 1. 扫描其他 Schema（使用精确正则匹配，避免误判 json-schema: 前缀）
        repository.findAll().forEach { other ->
            if (other.schemaName != schemaName &&
                containsReferenceTo(other.schemaJson.orEmpty(), schemaName)
            ) {
                referrers += "Schema[${other.schemaName}]"
            }
        }

        // 2. 扫描工作流定义的 inputSchema / outputSchema
        definitionRepository.findAll().forEach { wf ->
            val inInput = wf.inputSchema?.let { containsReferenceTo(it, schemaName) } == true
            val inOutput = wf.outputSchema?.let { containsReferenceTo(it, schemaName) } == true
            if (inInput || inOutput) {
                referrers += "Workflow[${wf.workflowName}]"
            }
        }

        return referrers
    }

    /**
     * 查询某 Schema 的所有版本（当前实现：仅返回自身作为唯一版本）
     */
    fun findVersions(schemaName: String): List<WfSchema> {
        val entity = repository.findBySchemaName(schemaName).orElse(null) ?: return emptyList()
        return listOf(entity)
    }

    // ─── Schema 配置分发 ────────────────────────────────────────────

    /**
     * 全量拉取所有 Schema 快照（供 Worker HTTP 拉取）
     */
    fun loadAllSchemaSnapshots(): List<SchemaConfigSnapshot> {
        return repository.findAll().map { toSnapshot(it) }
    }

    /**
     * 获取单个 Schema 快照
     */
    fun getSnapshot(schemaName: String): SchemaConfigSnapshot? {
        val entity = repository.findBySchemaName(schemaName).orElse(null) ?: return null
        return toSnapshot(entity)
    }

    private fun publishToWorkers(entity: WfSchema) {
        val publisher = schemaConfigPublisher ?: return
        asyncScope.launch {
            try {
                val snapshot = toSnapshot(entity)
                publisher.publish(snapshot)
            } catch (e: Exception) {
                log.error("Failed to publish schema [${entity.schemaName}] to workers", e)
                // 不阻断 DB 事务，降级为仅本地（下次发布可重试）
            }
        }
    }

    private fun unpublishFromWorkers(schemaName: String) {
        val publisher = schemaConfigPublisher ?: return
        asyncScope.launch {
            try {
                publisher.unpublish(schemaName)
            } catch (e: Exception) {
                log.error("Failed to unpublish schema [$schemaName] from workers", e)
            }
        }
    }

    private fun toSnapshot(entity: WfSchema): SchemaConfigSnapshot {
        return SchemaConfigSnapshot(
            schemaName = entity.schemaName,
            schemaFormat = entity.schemaFormat,
            schemaJson = entity.schemaJson,
            version = entity.id,
            description = entity.description,
            scope = entity.scope,
            appGroup = entity.appGroup,
            enabled = true
        )
    }
}
