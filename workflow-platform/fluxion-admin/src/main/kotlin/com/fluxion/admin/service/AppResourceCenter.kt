package com.fluxion.admin.service

import com.fluxion.admin.entity.App
import com.fluxion.admin.entity.AppResource
import com.fluxion.admin.entity.AppResourceBinding
import com.fluxion.admin.repository.AppRepository
import com.fluxion.admin.repository.AppResourceBindingRepository
import com.fluxion.admin.repository.AppResourceRepository
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application + Infrastructure-resource management service — the single business entry
 * point for the new "App Center" introduced to align builtin:dbExecute / redisCommand
 * datasources with the *real* Worker application context (and enable debug sandboxes).
 *
 * Covers three logical domains:
 * 1. **`App` lifecycle**              — CRUD, activate/deactivate, uniqueness checks.
 * 2. **`AppResource` registry**      — global catalog of DB / REDIS / KAFKA / ... configs;
 *                                      sensitive fields in `configJson` are transparently
 *                                      encrypted/decrypted by [EncryptedJsonMapConverter].
 * 3. **Binding (App ↔ Resource)**    — N:M junction with per-app alias + scope hints; the
 *                                      designer's node-param dropdowns call
 *                                      [listAppResourceOptions] to render the selector.
 *
 * Methods here intentionally return plain entity types; controllers project them into
 * HTTP-friendly DTOs so the persistence model stays free of view concerns.
 */
@Service
class AppResourceCenter(
    private val appRepo: AppRepository,
    private val resourceRepo: AppResourceRepository,
    private val bindingRepo: AppResourceBindingRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ═══════════════════════════════════════════════════════════════
    // 1. App CRUD
    // ═══════════════════════════════════════════════════════════════

    fun findAllApps(): List<App> = appRepo.findAll()

    fun findAppById(id: Long): App? = appRepo.findById(id).orElse(null)

    fun findAppByKey(key: String): App? = appRepo.findByAppKey(key).orElse(null)

    @Transactional
    fun createApp(dto: AppCreateDto): App {
        require(dto.appKey.isNotBlank()) { "appKey is required" }
        require(dto.appName.isNotBlank()) { "appName is required" }
        appRepo.findByAppKey(dto.appKey).ifPresent {
            throw IllegalArgumentException("App with appKey=${dto.appKey} already exists")
        }
        return App().apply {
            appKey = dto.appKey.trim()
            appName = dto.appName.trim()
            description = dto.description?.trim()
            owner = dto.owner
            status = dto.status ?: "ACTIVE"
        }.let(appRepo::save).also {
            log.info { "App created: id=${it.id} key=${it.appKey} name=${it.appName}" }
        }
    }

    @Transactional
    fun updateApp(id: Long, dto: AppUpdateDto): App {
        val existing = requireNotNull(appRepo.findById(id).orElse(null)) {
            "App id=$id not found"
        }
        dto.appName?.takeIf { it.isNotBlank() }?.let { existing.appName = it.trim() }
        dto.description?.let { existing.description = it }
        dto.owner?.let { existing.owner = it }
        dto.status?.takeIf { it.isNotBlank() }?.let { existing.status = it }
        return appRepo.save(existing)
    }

    @Transactional
    fun deleteApp(id: Long) {
        val existed = appRepo.existsById(id)
        require(existed) { "App id=$id not found" }
        appRepo.deleteById(id)
        log.warn { "App id=$id deleted (cascade-deleted bindings too)" }
    }

    data class AppCreateDto(
        val appKey: String,
        val appName: String,
        val description: String? = null,
        val owner: String? = null,
        val status: String? = "ACTIVE"
    )
    data class AppUpdateDto(
        val appName: String? = null,
        val description: String? = null,
        val owner: String? = null,
        val status: String? = null
    )

    // ═══════════════════════════════════════════════════════════════
    // 2. AppResource (global catalog) CRUD
    // ═══════════════════════════════════════════════════════════════

    fun findAllResources(resourceType: String? = null): List<AppResource> =
        if (resourceType == null) resourceRepo.findAll()
        else resourceRepo.findByResourceType(resourceType)

    fun findResourceById(id: Long): AppResource? = resourceRepo.findById(id).orElse(null)

    @Transactional
    fun createResource(dto: ResourceCreateDto): AppResource {
        require(dto.resourceName.isNotBlank()) { "resourceName is required" }
        require(dto.resourceType.isNotBlank()) { "resourceType is required" }
        resourceRepo.findByResourceName(dto.resourceName).ifPresent {
            throw IllegalArgumentException("Resource ${dto.resourceName} already exists")
        }
        return AppResource().apply {
            resourceType = dto.resourceType.trim()
            resourceName = dto.resourceName.trim()
            driver = dto.driver?.trim()
            configJson = dto.configJson.toMutableMap()
        }.let(resourceRepo::save).also {
            log.info { "AppResource created: id=${it.id} type=${it.resourceType} name=${it.resourceName}" }
        }
    }

    @Transactional
    fun updateResource(id: Long, dto: ResourceUpdateDto): AppResource {
        val r = requireNotNull(resourceRepo.findById(id).orElse(null)) { "Resource id=$id not found" }
        dto.resourceName?.takeIf { it.isNotBlank() }?.let { r.resourceName = it.trim() }
        dto.resourceType?.takeIf { it.isNotBlank() }?.let { r.resourceType = it.trim() }
        dto.driver?.let { r.driver = it }
        if (dto.configJson != null) r.configJson = dto.configJson.toMutableMap()
        return resourceRepo.save(r)
    }

    @Transactional
    fun deleteResource(id: Long) {
        val bound = bindingRepo.findByAppIdAndResourceId(0L, id) // quick hint check
        // Delegate to RESTRICT FK — if any binding still points here, SQL will throw
        resourceRepo.deleteById(id)
        log.warn { "AppResource id=$id deleted" }
    }

    data class ResourceCreateDto(
        val resourceType: String,
        val resourceName: String,
        val driver: String? = null,
        val configJson: Map<String, Any?> = emptyMap()
    )
    data class ResourceUpdateDto(
        val resourceName: String? = null,
        val resourceType: String? = null,
        val driver: String? = null,
        val configJson: Map<String, Any?>? = null
    )

    // ═══════════════════════════════════════════════════════════════
    // 3. App↔Resource Binding
    // ═══════════════════════════════════════════════════════════════

    /** All bindings for an app, with the [AppResource] JOIN FETCHed — supports the App-details page. */
    @Transactional(readOnly = true)
    fun listBindings(appId: Long): List<AppResourceBinding> =
        bindingRepo.findAllByAppIdWithResource(appId)

    /** Type-filtered bindings with aliases — returned to node-param dropdowns in the designer. */
    @Transactional(readOnly = true)
    fun listAppResourceOptions(appId: Long, resourceType: String): List<ResourceOption> =
        bindingRepo.findByAppIdAndResourceTypeWithResource(appId, resourceType).map { b ->
            ResourceOption(
                bindingId = b.id,
                resourceId = b.resource.id,
                alias = b.effectiveAlias(),
                resourceName = b.resource.resourceName,
                resourceType = b.resource.resourceType,
                driver = b.resource.driver,
                scope = b.resourceScope,
                isDefault = b.resourceScope.equals("DEFAULT", ignoreCase = true)
            )
        }

    @Transactional
    fun bindResource(appId: Long, dto: BindingCreateDto): AppResourceBinding {
        val app = requireNotNull(appRepo.findById(appId).orElse(null)) { "App id=$appId not found" }
        val res = requireNotNull(resourceRepo.findById(dto.resourceId).orElse(null)) {
            "Resource id=${dto.resourceId} not found"
        }
        bindingRepo.findByAppIdAndResourceId(appId, dto.resourceId)?.let {
            throw IllegalArgumentException(
                "App id=$appId already bound to Resource id=${dto.resourceId} (bindingId=${it.id})"
            )
        }
        return AppResourceBinding().apply {
            this.app = app
            this.resource = res
            this.resourceScope = dto.resourceScope ?: "DEFAULT"
            this.aliasInApp = dto.aliasInApp?.trim()?.ifBlank { null }
        }.let(bindingRepo::save)
    }

    @Transactional
    fun updateBinding(appId: Long, bindingId: Long, dto: BindingUpdateDto): AppResourceBinding {
        val b = requireNotNull(bindingRepo.findById(bindingId).orElse(null)) { "Binding id=$bindingId not found" }
        require(b.app.id == appId) { "Binding id=$bindingId does not belong to App id=$appId" }
        dto.resourceScope?.takeIf { it.isNotBlank() }?.let { b.resourceScope = it }
        dto.aliasInApp?.let { b.aliasInApp = it.trim().ifBlank { null } }
        return bindingRepo.save(b)
    }

    @Transactional
    fun unbind(appId: Long, bindingId: Long) {
        val b = requireNotNull(bindingRepo.findById(bindingId).orElse(null)) { "Binding id=$bindingId not found" }
        require(b.app.id == appId) { "Binding id=$bindingId does not belong to App id=$appId" }
        bindingRepo.delete(b)
    }

    data class BindingCreateDto(
        val resourceId: Long,
        val resourceScope: String? = "DEFAULT",
        val aliasInApp: String? = null
    )
    data class BindingUpdateDto(
        val resourceScope: String? = null,
        val aliasInApp: String? = null
    )
    data class ResourceOption(
        val bindingId: Long,
        val resourceId: Long,
        val alias: String,
        val resourceName: String,
        val resourceType: String,
        val driver: String?,
        val scope: String,
        val isDefault: Boolean
    )
}
