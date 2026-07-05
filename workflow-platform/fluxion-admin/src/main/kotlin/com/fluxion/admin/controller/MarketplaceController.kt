package com.fluxion.admin.controller

import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.service.MarketplaceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 工作流市场 REST API
 *
 * 提供市场浏览、搜索、发布、安装、卸载等功能。
 */
@RestController
@RequestMapping("/api/admin/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService,
    private val securityContext: SecurityContextHelper,
) {

    /**
     * 列出所有 ACTIVE 的市场 listing
     */
    @GetMapping
    fun listListings(): ResponseEntity<List<Map<String, Any?>>> {
        val listings = marketplaceService.listActive()
        return ResponseEntity.ok(listings.map { it.toMap() })
    }

    /**
     * 搜索市场
     */
    @GetMapping("/search")
    fun search(@RequestParam keyword: String): ResponseEntity<List<Map<String, Any?>>> {
        val listings = marketplaceService.search(keyword)
        return ResponseEntity.ok(listings.map { it.toMap() })
    }

    /**
     * 获取单个 listing 详情
     */
    @GetMapping("/{listingId}")
    fun getListing(@PathVariable listingId: String): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.getListing(listingId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(listing.toMap())
    }

    /**
     * 发布工作流到市场
     */
    @PostMapping("/publish/workflow")
    fun publishWorkflow(@RequestBody request: PublishRequest): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.publishWorkflow(
            listingId = request.listingId,
            sourceId = request.sourceId,
            title = request.title,
            description = request.description,
            tags = request.tags?.let { com.fluxion.core.util.JsonUtil.serialize(it) },
            author = securityContext.currentUsername(),
        )
        return ResponseEntity.ok(listing.toMap())
    }

    /**
     * 发布函数到市场
     */
    @PostMapping("/publish/function")
    fun publishFunction(@RequestBody request: PublishRequest): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.publishFunction(
            listingId = request.listingId,
            sourceId = request.sourceId,
            title = request.title,
            description = request.description,
            tags = request.tags?.let { com.fluxion.core.util.JsonUtil.serialize(it) },
            author = securityContext.currentUsername(),
        )
        return ResponseEntity.ok(listing.toMap())
    }

    /**
     * 发布工作流模版到市场
     */
    @PostMapping("/publish/template")
    fun publishTemplate(@RequestBody request: PublishTemplateRequest): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.publishTemplate(
            listingId = request.listingId,
            sourceId = request.sourceId,
            title = request.title,
            description = request.description,
            tags = request.tags?.let { com.fluxion.core.util.JsonUtil.serialize(it) },
            author = securityContext.currentUsername(),
            templateConfig = request.templateConfig?.let { com.fluxion.core.util.JsonUtil.serialize(it) },
        )
        return ResponseEntity.ok(listing.toMap())
    }

    /**
     * 安装市场工作流/函数到指定 app_group
     *
     * 需要身份验证：当前用户必须是目标 app_group 的成员
     */
    @PostMapping("/install")
    fun install(@RequestBody request: InstallRequest): ResponseEntity<Map<String, Any>> {
        // 租户权限校验
        securityContext.requireAppGroupAccess(request.appGroup)

        val listing = marketplaceService.getListing(request.listingId)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Listing not found"))

        val result = when (listing.sourceType) {
            "WORKFLOW" -> marketplaceService.installWorkflow(
                request.listingId, request.appGroup, securityContext.currentUsername()
            ).let { mapOf("type" to "WORKFLOW", "id" to it.workflowId, "name" to it.workflowName) }
            "FUNCTION" -> marketplaceService.installFunction(
                request.listingId, request.appGroup, securityContext.currentUsername()
            ).let { mapOf("type" to "FUNCTION", "id" to it.functionName, "name" to it.functionName) }
            "WORKFLOW_TEMPLATE" -> marketplaceService.installTemplate(
                request.listingId, request.appGroup, securityContext.currentUsername(),
                request.templateParams ?: emptyMap()
            ).let { mapOf("type" to "WORKFLOW_TEMPLATE", "id" to it.workflowId, "name" to it.workflowName) }
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "Unknown source type"))
        }
        return ResponseEntity.ok(result)
    }

    /**
     * 卸载市场安装
     */
    @DeleteMapping("/install")
    fun uninstall(@RequestParam listingId: String, @RequestParam appGroup: String): ResponseEntity<Unit> {
        securityContext.requireAppGroupAccess(appGroup)
        marketplaceService.uninstall(listingId, appGroup)
        return ResponseEntity.noContent().build()
    }

    /**
     * 获取某 app_group 的安装记录
     */
    @GetMapping("/installs")
    fun getInstalls(@RequestParam appGroup: String): ResponseEntity<List<Map<String, Any?>>> {
        securityContext.requireAppGroupAccess(appGroup)
        val installs = marketplaceService.getInstalls(appGroup)
        return ResponseEntity.ok(installs.map { install ->
            mapOf(
                "listingId" to install.listingId,
                "appGroup" to install.appGroup,
                "installedBy" to install.installedBy,
                "createdAt" to install.createdAt.toString(),
            )
        })
    }

    // ─── DTO ────────────────────────────────────────────────────────

    data class PublishRequest(
        val listingId: String,
        val sourceId: String,
        val title: String,
        val description: String? = null,
        val tags: List<String>? = null,
    )

    data class PublishTemplateRequest(
        val listingId: String,
        val sourceId: String,
        val title: String,
        val description: String? = null,
        val tags: List<String>? = null,
        val templateConfig: Map<String, Any>? = null,
    )

    data class InstallRequest(
        val listingId: String,
        val appGroup: String,
        val templateParams: Map<String, Any>? = null,
    )

    // ─── Helper ──────────────────────────────────────────────────────

    private fun com.fluxion.admin.entity.WfMarketplaceListing.toMap(): Map<String, Any?> = mapOf(
        "listingId" to listingId,
        "sourceType" to sourceType,
        "sourceId" to sourceId,
        "sourceVersion" to sourceVersion,
        "title" to title,
        "description" to description,
        "tags" to tags,
        "author" to author,
        "status" to status,
        "installCount" to installCount,
        "templateConfig" to templateConfig,
        "createdAt" to createdAt.toString(),
        "updatedAt" to updatedAt.toString(),
    )
}
