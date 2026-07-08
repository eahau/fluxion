package com.fluxion.admin.controller

import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.service.MarketplaceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Marketplace REST controller — browse / publish / install / uninstall workflows,
 * functions, and parameterised workflow templates.
 *
 * All mutation endpoints (publish, install, uninstall) are tenant-gated:
 *   - publishers must be able to access the source resource's appGroup
 *   - installers must be members of the target appGroup
 *
 * Request/response DTOs are declared inline because this API is not yet part of
 * the generated OpenAPI spec (hand-written JSON maps keep iteration fast).
 *
 * Collaborates with: MarketplaceService (business logic), SecurityContextHelper
 * (tenant access checks + current user for audit fields on install receipts).
 */
@RestController
@RequestMapping("/api/admin/marketplace")
class MarketplaceController(
    private val marketplaceService: MarketplaceService,
    private val securityContext: SecurityContextHelper,
) {

    /** List every ACTIVE marketplace listing (browse index). */
    @GetMapping
    fun listListings(): ResponseEntity<List<Map<String, Any?>>> {
        val listings = marketplaceService.listActive()
        return ResponseEntity.ok(listings.map { it.toMap() })
    }

    /** Keyword search over title + description (case-insensitive, both OR'd). */
    @GetMapping("/search")
    fun search(@RequestParam keyword: String): ResponseEntity<List<Map<String, Any?>>> {
        val listings = marketplaceService.search(keyword)
        return ResponseEntity.ok(listings.map { it.toMap() })
    }

    /** Fetch a single listing detail by stable listing id. */
    @GetMapping("/{listingId}")
    fun getListing(@PathVariable listingId: String): ResponseEntity<Map<String, Any?>> {
        val listing = marketplaceService.getListing(listingId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(listing.toMap())
    }

    /** Publish a workflow definition to the marketplace (author = current session user). */
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

    /** Publish a registered function to the marketplace. */
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

    /** Publish a parameterised workflow template to the marketplace. */
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
     * Install a listing into a consumer's appGroup.
     *
     * Creates a scope=PRIVATE copy whose sourceRef points back to the original.
     * Dispatches to the correct service method based on listing.sourceType.
     */
    @PostMapping("/install")
    fun install(@RequestBody request: InstallRequest): ResponseEntity<Map<String, Any>> {
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

    /** Uninstall a listing → delete the PRIVATE copy and roll back the install count. */
    @DeleteMapping("/install")
    fun uninstall(@RequestParam listingId: String, @RequestParam appGroup: String): ResponseEntity<Unit> {
        securityContext.requireAppGroupAccess(appGroup)
        marketplaceService.uninstall(listingId, appGroup)
        return ResponseEntity.noContent().build()
    }

    /** List every install receipt for an app_group (team installed-apps screen). */
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

    // ─── Request DTOs ────────────────────────────────────────────────

    /** Payload for workflow / function publish endpoints. */
    data class PublishRequest(
        val listingId: String,
        val sourceId: String,
        val title: String,
        val description: String? = null,
        val tags: List<String>? = null,
    )

    /** Extended publish payload for templates (carries a template parameter map). */
    data class PublishTemplateRequest(
        val listingId: String,
        val sourceId: String,
        val title: String,
        val description: String? = null,
        val tags: List<String>? = null,
        val templateConfig: Map<String, Any>? = null,
    )

    /** Install a listing into an appGroup; carries optional template params. */
    data class InstallRequest(
        val listingId: String,
        val appGroup: String,
        val templateParams: Map<String, Any>? = null,
    )

    // ─── Entity → Map conversion helper ──────────────────────────────

    /** Convert a DB listing entity to the UI's flat map response format. */
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
