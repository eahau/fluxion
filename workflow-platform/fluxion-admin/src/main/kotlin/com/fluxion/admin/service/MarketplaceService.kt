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
import org.slf4j.info
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Internal marketplace service for sharing and installing workflows, functions,
 * and workflow templates across app_groups.
 *
 * Operation model:
 *   - PUBLISHER takes a PLATFORM / private resource and creates a
 *     `wf_marketplace_listing` row pointing at it (source ref + title + tags)
 *   - CONSUMER installs a listing into their own app_group, which produces a
 *     scope=PRIVATE deep-copy of the original workflow/function (suffix:
 *     `{sourceId}-{appGroup}`) and increments the listing's install counter
 *   - UNINSTALL removes the PRIVATE copy, decrements install count, and removes
 *     the install receipt row
 *   - TEMPLATE installs additionally run `{{paramKey}}` string substitution over
 *     dagJson using the caller-supplied parameter map
 *
 * Collaborates with: WfMarketplaceListingRepository, WfMarketplaceInstallRepository
 * (listing + receipt persistence), WfDefinitionRepository / WfFunctionRepository
 * (source lookup + PRIVATE copy creation), SecurityContextHelper (publish-time
 * tenant access check — you can only publish a resource whose app_group you can access).
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

    // ===== Publish =====

    /**
     * Publish an existing workflow definition to the marketplace.
     *
     * Requires the caller to have access to the source workflow's app_group (when
     * not PLATFORM scope). Throws IllegalArgumentException when the same workflow
     * is already listed under a different listing id.
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
            .orElseThrow { IllegalArgumentException("Workflow not found: $sourceId") }

        definition.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val existing = listingRepository.findBySourceTypeAndSourceId("WORKFLOW", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("Workflow [$sourceId] is already published in marketplace")
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
     * Publish a registered function to the marketplace.
     *
     * Semantics mirror [publishWorkflow]: tenant access gate + duplicate-listing check.
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
            .orElseThrow { IllegalArgumentException("Function not found: $sourceId") }

        function.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val existing = listingRepository.findBySourceTypeAndSourceId("FUNCTION", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("Function [$sourceId] is already published in marketplace")
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
     * Publish a parameterized workflow template.
     *
     * Same lifecycle as [publishWorkflow] plus a [templateConfig] JSON blob that
     * describes which `{{paramKey}}` placeholders exist and how they should be
     * rendered in the install UI. The template can be later installed either via
     * [installTemplate] (with params) or the generic install workflow path.
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
            .orElseThrow { IllegalArgumentException("Workflow not found: $sourceId") }

        definition.appGroup?.let { securityContext.requireAppGroupAccess(it) }

        val existing = listingRepository.findBySourceTypeAndSourceId("WORKFLOW_TEMPLATE", sourceId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("Workflow template [$sourceId] is already published in marketplace")
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

    // ===== Install =====

    /**
     * Install a marketplace workflow listing into a target app_group.
     *
     * Produces a new scope=PRIVATE [WfDefinition] copy whose id is
     * `{source.workflowId}-{appGroup}` and records the install receipt. Bumps the
     * listing.installCount counter so the browse UI can sort by popularity.
     */
    @Transactional
    fun installWorkflow(listingId: String, appGroup: String, installedBy: String?): WfDefinition {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("Marketplace listing not found: $listingId") }
        if (listing.sourceType != "WORKFLOW") {
            throw IllegalArgumentException("Listing [$listingId] is not a WORKFLOW type")
        }

        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("Already installed into app_group [$appGroup]")
        }

        val source = definitionRepository.findByWorkflowId(listing.sourceId)
            .orElseThrow { IllegalStateException("Source workflow not found: ${listing.sourceId}") }

        // Deep copy into a PRIVATE-scope row under the consumer's appGroup
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

        val install = WfMarketplaceInstall().apply {
            this.listingId = listingId
            this.appGroup = appGroup
            this.installedBy = installedBy
        }
        installRepository.save(install)

        listing.installCount = listing.installCount + 1
        listingRepository.save(listing)

        log.info { "Installed marketplace workflow [$listingId] to appGroup [$appGroup]" }
        return saved
    }

    /**
     * Install a marketplace function listing into a target app_group.
     * Creates a PRIVATE-scope [WfFunction] copy prefixed with the source name.
     */
    @Transactional
    fun installFunction(listingId: String, appGroup: String, installedBy: String?): WfFunction {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("Marketplace listing not found: $listingId") }
        if (listing.sourceType != "FUNCTION") {
            throw IllegalArgumentException("Listing [$listingId] is not a FUNCTION type")
        }

        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("Already installed into app_group [$appGroup]")
        }

        val source = functionRepository.findByFunctionName(listing.sourceId)
            .orElseThrow { IllegalStateException("Source function not found: ${listing.sourceId}") }

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
     * Install a parameterized marketplace template into a target app_group.
     *
     * After loading the source definition this runs `{{paramKey}}` replacement
     * over the dagJson (see [replaceTemplateParams]) before creating the PRIVATE
     * copy. All scalar fields (schemas, triggers, route bindings) are cloned
     * verbatim from the source row.
     */
    @Transactional
    fun installTemplate(
        listingId: String,
        appGroup: String,
        installedBy: String?,
        templateParams: Map<String, Any> = emptyMap()
    ): WfDefinition {
        val listing = listingRepository.findByListingId(listingId)
            .orElseThrow { IllegalArgumentException("Marketplace listing not found: $listingId") }
        if (listing.sourceType != "WORKFLOW_TEMPLATE" && listing.sourceType != "WORKFLOW") {
            throw IllegalArgumentException("Listing [$listingId] is not a WORKFLOW_TEMPLATE type")
        }

        if (installRepository.existsByListingIdAndAppGroup(listingId, appGroup)) {
            throw IllegalArgumentException("Already installed into app_group [$appGroup]")
        }

        val source = definitionRepository.findByWorkflowId(listing.sourceId)
            .orElseThrow { IllegalStateException("Source workflow not found: ${listing.sourceId}") }

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
     * Pure helper: replace every `{{key}}` token in the DAG JSON string with the
     * `toString()` rendering of the corresponding value in [params].
     *
     * NOTE: this is naive string substitution. Template parameters that need to
     * become JSON objects/arrays should be serialized to JSON in the caller
     * before being passed into the map.
     */
    private fun replaceTemplateParams(dagJson: String, params: Map<String, Any>): String {
        var result = dagJson
        params.forEach { (key, value) ->
            result = result.replace("{{${key}}}", value.toString())
        }
        return result
    }

    // ===== Uninstall =====

    /**
     * Reverse an install: delete the PRIVATE copy of the listing, remove the
     * install receipt, and roll back the listing's install counter (clamped at 0).
     */
    @Transactional
    fun uninstall(listingId: String, appGroup: String) {
        val install = installRepository.findByListingIdAndAppGroup(listingId, appGroup)
            ?: throw IllegalArgumentException("Not installed into app_group [$appGroup]")

        val listing = listingRepository.findByListingId(listingId).orElse(null)

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

        if (listing != null && listing.installCount > 0) {
            listing.installCount = listing.installCount - 1
            listingRepository.save(listing)
        }

        log.info { "Uninstalled marketplace listing [$listingId] from appGroup [$appGroup]" }
    }

    // ===== Browse / Search =====

    /** List every ACTIVE marketplace listing (admin browse view). */
    fun listActive(): List<WfMarketplaceListing> {
        return listingRepository.findByStatus("ACTIVE")
    }

    /**
     * Keyword search over ACTIVE marketplace listings. Matches case-insensitive
     * substring against title OR description (Spring Data generated OR query).
     */
    fun search(keyword: String): List<WfMarketplaceListing> {
        return listingRepository
            .findByStatusAndTitleContainingIgnoreCaseOrStatusAndDescriptionContainingIgnoreCase(
                "ACTIVE", keyword, "ACTIVE", keyword
            )
    }

    /** Fetch a single listing detail by its stable listing id. */
    fun getListing(listingId: String): WfMarketplaceListing? {
        return listingRepository.findByListingId(listingId).orElse(null)
    }

    /** All install receipts for a given app_group (team "installed apps" view). */
    fun getInstalls(appGroup: String): List<WfMarketplaceInstall> {
        return installRepository.findByAppGroup(appGroup)
    }
}
