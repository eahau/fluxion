package com.fluxion.admin.service

import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.entity.WfMarketplaceInstall
import com.fluxion.admin.entity.WfMarketplaceListing
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.admin.repository.WfMarketplaceInstallRepository
import com.fluxion.admin.repository.WfMarketplaceListingRepository
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.core.util.JsonUtil
import org.slf4j.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 市场服务
 *
 * 核心职责：
 *   1. 发布工作流/函数到市场
 *   2. 从市场安装（生成 PRIVATE 副本）
 *   3. 卸载市场安装
 *   4. 市场浏览与搜索
 */
@Service
class MarketplaceService(
    private val listingRepository: WfMarketplaceListingRepository,
    private val installRepository: WfMarketplaceInstallRepository,
    private val definitionRepository: WfDefinitionRepository,
    private val functionRepository: WfFunctionRepository,
    private val securityContext: SecurityContextHelper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ─── 发布 ────────────────────────────────────────────────────────

    /**
     * 发布工作流到市场
     *
     * @param listingId   市场唯一标识（如 user-login-flow）
     * @param sourceId    源工作流 workflowId
     * @param title       市场展示标题
     * @param description 描述
     * @param tags        标签列表
     * @param author      发布者
     */
    @Transactional
    fun publishWorkflow(
        listingId: String,
        sourceId: String,
        title: String,
        description: String?,
        tags: String?,
        author: String,
    ): WfMarketplaceListing {
        val definition = definitionRepository.findByWorkflowId(sourceId)
            .orElseThrow { IllegalArgumentException("工作流不存在: $sourceId") }

        // 租户权限校验：发布者必须有权访问源工作流的 appGroup
        definition.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        // 检查工作流是否已在市场中发布
        val existing = listingRepository.findBySourceTypeAndSourceId("WORKFLOW", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("工作流 [$sourceId] 已在市场发布")
        }

        val listing = WfMarketplaceListing().apply {
            this.listingId = listingId
            this.sourceType = "WORKFLOW"
            this.sourceId = sourceId
            this.sourceVersion = definition.version
            this.title = title
            this.description = description
            this.tags = tags
            this.author = author
            this.status = "ACTIVE"
        }
        return listingRepository.save(listing).also {
            log.info { "Published workflow [$sourceId] to marketplace as [$listingId]" }
        }
    }

    /**
     * 发布函数到市场
     */
    @Transactional
    fun publishFunction(
        listingId: String,
        sourceId: String,
        title: String,
        description: String?,
        tags: String?,
        author: String,
    ): WfMarketplaceListing {
        val function = functionRepository.findByFunctionName(sourceId)
            .orElseThrow { IllegalArgumentException("函数不存在: $sourceId") }

        // 租户权限校验：发布者必须有权访问源函数的 appGroup
        function.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val existing = listingRepository.findBySourceTypeAndSourceId("FUNCTION", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("函数 [$sourceId] 已在市场发布")
        }

        val listing = WfMarketplaceListing().apply {
            this.listingId = listingId
            this.sourceType = "FUNCTION"
            this.sourceId = sourceId
            this.sourceVersion = 1
            this.title = title
            this.description = description
            this.tags = tags
            this.author = author
            this.status = "ACTIVE"
        }
        return listingRepository.save(listing).also {
            log.info { "Published function [$sourceId] to marketplace as [$listingId]" }
        }
    }

    /**
     * 发布工作流模版到市场
     *
     * @param templateConfig 模版配置 JSON（定义可替换参数列表）
     */
    @Transactional
    fun publishTemplate(
        listingId: String,
        sourceId: String,
        title: String,
        description: String?,
        tags: String?,
        author: String,
        templateConfig: String?,
    ): WfMarketplaceListing {
        val definition = definitionRepository.findByWorkflowId(sourceId)
            .orElseThrow { IllegalArgumentException("工作流不存在: $sourceId") }

        definition.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val existing = listingRepository.findBySourceTypeAndSourceId("WORKFLOW_TEMPLATE", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("工作流模版 [$sourceId] 已在市场发布")
        }

        val listing = WfMarketplaceListing().apply {
            this.listingId = listingId
            this.sourceType = "WORKFLOW_TEMPLATE"
            this.sourceId = sourceId
            this.sourceVersion = definition.version
            this.title = title
            this.description = description
            this.tags = tags
            this.author = author
            this.status = "ACTIVE"
            this.templateConfig = templateConfig
        }
        return listingRepository.save(listing).also {
            log.info { "Published workflow template [$sourceId] to marketplace as [$listingId]" }
        }
    }

    // ─── 安装 ────────────────────────────────────────────────────────

    /**
     * 安装市场工作流到指定 app_group
     *
     * 生成一个 scope=PRIVATE 的副本，source_ref 指向市场原始工作流
     */
    @Transactional
    fun installWorkflow(listingId: String, appGroup: String, installedBy: String?): WfDefinition {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("市场 listing 不存在: $listingId") }
        if (listing.sourceType != "WORKFLOW") {
            throw IllegalArgumentException("Listing [$listingId] 不是工作流类型")
        }

        // 检查是否已安装
        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("已安装到 app_group [$appGroup]")
        }

        val source = definitionRepository.findByWorkflowId(listing.sourceId)
            .orElseThrow { IllegalStateException("源工作流不存在: ${listing.sourceId}") }

        // 生成 PRIVATE 副本
        val copy = WfDefinition().apply {
            this.workflowId = "${source.workflowId}-${appGroup}"
            this.workflowName = source.workflowName
            this.dagJson = source.dagJson
            this.inputSchema = source.inputSchema
            this.outputSchema = source.outputSchema
            this.category = source.category
            this.scope = "PRIVATE"
            this.appGroup = appGroup
            this.sourceRef = source.workflowId
            this.protocol = source.protocol
            this.method = source.method
            this.bindKey = source.bindKey
            this.status = "DRAFT"
            this.version = 1
        }
        val saved = definitionRepository.save(copy)

        // 记录安装
        val install = WfMarketplaceInstall().apply {
            this.listingId = listingId
            this.appGroup = appGroup
            this.installedBy = installedBy
        }
        installRepository.save(install)

        // 更新安装次数
        listing.installCount = listing.installCount + 1
        listingRepository.save(listing)

        log.info { "Installed marketplace workflow [$listingId] to appGroup [$appGroup]" }
        return saved
    }

    /**
     * 安装市场函数到指定 app_group
     */
    @Transactional
    fun installFunction(listingId: String, appGroup: String, installedBy: String?): WfFunction {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("市场 listing 不存在: $listingId") }
        if (listing.sourceType != "FUNCTION") {
            throw IllegalArgumentException("Listing [$listingId] 不是函数类型")
        }

        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("已安装到 app_group [$appGroup]")
        }

        val source = functionRepository.findByFunctionName(listing.sourceId)
            .orElseThrow { IllegalStateException("源函数不存在: ${listing.sourceId}") }

        val copy = WfFunction().apply {
            this.functionName = "${source.functionName}-${appGroup}"
            this.functionType = source.functionType
            this.scope = "PRIVATE"
            this.appGroup = appGroup
            this.sourceRef = source.functionName
            this.domain = source.domain
            this.config = source.config
        }
        val saved = functionRepository.save(copy)

        val install = WfMarketplaceInstall().apply {
            this.listingId = listingId
            this.appGroup = appGroup
            this.installedBy = installedBy
        }
        installRepository.save(install)

        listing.installCount = listing.installCount + 1
        listingRepository.save(listing)

        log.info { "Installed marketplace function [$listingId] to appGroup [$appGroup]" }
        return saved
    }

    /**
     * 安装模版工作流到指定 app_group
     *
     * 与 installWorkflow 类似，但会根据模版配置替换参数值。
     *
     * @param templateParams 用户提供的模版参数（key=参数名，value=参数值）
     */
    @Transactional
    fun installTemplate(
        listingId: String,
        appGroup: String,
        installedBy: String?,
        templateParams: Map<String, Any> = emptyMap()
    ): WfDefinition {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("市场 listing 不存在: $listingId") }
        if (listing.sourceType != "WORKFLOW_TEMPLATE" && listing.sourceType != "WORKFLOW") {
            throw IllegalArgumentException("Listing [$listingId] 不是工作流模版类型")
        }

        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("已安装到 app_group [$appGroup]")
        }

        val source = definitionRepository.findByWorkflowId(listing.sourceId)
            .orElseThrow { IllegalStateException("源工作流不存在: ${listing.sourceId}") }

        // 基于模版参数替换 DAG JSON 中的占位符
        val processedDagJson = if (templateParams.isNotEmpty()) {
            replaceTemplateParams(source.dagJson, templateParams)
        } else {
            source.dagJson
        }

        val copy = WfDefinition().apply {
            this.workflowId = "${source.workflowId}-${appGroup}"
            this.workflowName = source.workflowName
            this.dagJson = processedDagJson
            this.inputSchema = source.inputSchema
            this.outputSchema = source.outputSchema
            this.category = source.category
            this.scope = "PRIVATE"
            this.appGroup = appGroup
            this.sourceRef = source.workflowId
            this.protocol = source.protocol
            this.method = source.method
            this.bindKey = source.bindKey
            this.status = "DRAFT"
            this.version = 1
            this.triggersConfig = source.triggersConfig
        }
        val saved = definitionRepository.save(copy)

        val install = WfMarketplaceInstall().apply {
            this.listingId = listingId
            this.appGroup = appGroup
            this.installedBy = installedBy
        }
        installRepository.save(install)

        listing.installCount = listing.installCount + 1
        listingRepository.save(listing)

        log.info { "Installed marketplace template [$listingId] to appGroup [$appGroup]" }
        return saved
    }

    /**
     * 替换 DAG JSON 中的模版占位符。
     *
     * 占位符格式：`{{paramKey}}`
     */
    private fun replaceTemplateParams(dagJson: String, params: Map<String, Any>): String {
        var result = dagJson
        params.forEach { (key, value) ->
            result = result.replace("{{${key}}}", value.toString())
        }
        return result
    }

    // ─── 卸载 ────────────────────────────────────────────────────────

    /**
     * 卸载市场安装
     */
    @Transactional
    fun uninstall(listingId: String, appGroup: String) {
        val install = installRepository.findByListingIdAndAppGroup(listingId, appGroup)
            ?: throw IllegalArgumentException("未安装到 app_group [$appGroup]")

        val listing = listingRepository.findByListingId(listingId).orElse(null)

        // 删除 PRIVATE 副本
        if (listing != null) {
            when (listing.sourceType) {
                "WORKFLOW", "WORKFLOW_TEMPLATE" -> {
                    val copyId = "${listing.sourceId}-${appGroup}"
                    definitionRepository.findByWorkflowId(copyId).ifPresent { def ->
                        definitionRepository.delete(def)
                        log.info { "Deleted PRIVATE workflow copy [$copyId]" }
                    }
                }
                "FUNCTION" -> {
                    val copyName = "${listing.sourceId}-${appGroup}"
                    functionRepository.findByFunctionName(copyName).ifPresent { fn ->
                        functionRepository.delete(fn)
                        log.info { "Deleted PRIVATE function copy [$copyName]" }
                    }
                }
            }
        }

        installRepository.delete(install)

        // 回退安装次数
        if (listing != null && listing.installCount > 0) {
            listing.installCount = listing.installCount - 1
            listingRepository.save(listing)
        }

        log.info { "Uninstalled marketplace listing [$listingId] from appGroup [$appGroup]" }
    }

    // ─── 浏览 / 搜索 ─────────────────────────────────────────────────

    /**
     * 列出所有 ACTIVE 的市场 listing
     */
    fun listActive(): List<WfMarketplaceListing> {
        return listingRepository.findByStatus("ACTIVE")
    }

    /**
     * 按关键词搜索市场
     */
    fun search(keyword: String): List<WfMarketplaceListing> {
        return listingRepository
            .findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
                "ACTIVE", keyword, "ACTIVE", keyword
            )
    }

    /**
     * 获取单个 listing 详情
     */
    fun getListing(listingId: String): WfMarketplaceListing? {
        return listingRepository.findByListingId(listingId).orElse(null)
    }

    /**
     * 获取某 app_group 的安装记录
     */
    fun getInstalls(appGroup: String): List<WfMarketplaceInstall> {
        return installRepository.findByAppGroup(appGroup)
    }
}
