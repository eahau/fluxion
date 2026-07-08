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
import org.slf4j.*
import org.slf4j.error
import org.slf4j.info
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Business service for managing [WfSchema] data-schema definitions.
 *
 * Responsibilities:
 *   1. CRUD on the `wf_schema` table via [WfSchemaRepository]
 *   2. Asynchronously publish schema mutations to Worker instances through the
 *      optional [SchemaConfigPublisher] SPI (so runtime validation stays in sync)
 *   3. Provide full and single-schema snapshot endpoints for Worker HTTP pull bootstrap
 *   4. Permission-aware frozen-state enforcement and schema-reference integrity checks
 *
 * Collaborates with: SecurityContextHelper (tenant visibility), SchemaConfigPublisher
 * (worker push), WfDefinitionRepository (referrer back-link checks), SchemaCompatibilityValidator
 * (upgrade-policy guard wired in the caller).
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
        /** Permission required to edit/deprecate a frozen schema. */
        const val SCHEMA_UNLOCK_PERMISSION = "schema:unlock"

        /** Permission required for general schema edit mutations. */
        const val SCHEMA_EDIT_PERMISSION = "schema:edit"

        /** Prefix used inside JSON Schema `$ref` values to reference a registered schema by name. */
        const val SCHEMA_REF_PREFIX = "schema:"

        /**
         * Matches `$ref` entries that point to a schema via the `schema:` prefix.
         *
         * Anchors the lookbehind to a leading `"` before the `$ref` key so that references
         * under a different protocol (e.g. `json-schema:`) do not accidentally match.
         */
        private val SCHEMA_REF_REGEX = Regex("\"\\\$ref\"\\s*:\\s*\"schema:([^\"]+)\"")

        /**
         * Returns true if the JSON payload contains an exact `schema:schemaName` reference.
         */
        private fun containsReferenceTo(schemaJson: String, schemaName: String): Boolean {
            return SCHEMA_REF_REGEX.findAll(schemaJson)
                .any { it.groupValues[1] == schemaName }
        }
    }

    /**
     * Returns true when the current security context holds the requested permission.
     *
     * Mirrors the semantics in SecurityContextHelper.isAdmin exactly so the UI and backend
     * never disagree on frozen/unlock capability:
     *   - No auth / AnonymousAuthenticationToken → local/dev bypass = ADMIN = all permissions
     *   - Authenticated user → exact permission match, or ROLE_ADMIN / ADMIN authority grants
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

    /**
     * Paginated search that returns lightweight [WfSchema] projections (schemaJson is
     * stripped). Delegates to [searchSummaries] and rebuilds the entity skeleton so the
     * DTO mapper contract stays unchanged.
     */
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
     * Lightweight listing query optimised for the admin schema list page.
     *
     * Performance design (addresses the two hotspots identified in profiling):
     * 1. Uses `SELECT NEW` JPQL to project [WfSchemaSummary] without loading the large
     *    `schemaJson` TEXT column — reduces JDBC I/O from ~2MB to ~20KB per page.
     * 2. Pushes keyword/scope filtering down to SQL instead of calling `findAll()`.
     * 3. Applies the comma-separated `schemaType` filter in memory (JPQL cannot express
     *    exact multi-tag matching); the lightweight DTO keeps 1000 rows at ~200KB.
     * 4. Performs the final pagination in memory to preserve identical ordering semantics
     *    to the previous `findAll() + page` implementation.
     */
    fun searchSummaries(keyword: String?, schemaType: String?, pageable: Pageable): Page<WfSchemaSummary> {
        val accessibleGroups = securityContext.accessibleAppGroups()

        // Scope filter encoding: 0 = only PLATFORM, 1 = PLATFORM + user's PRIVATE groups, 2 = everything
        val (scopeFilter, appGroups) = when {
            accessibleGroups == null -> 2 to emptyList<String>()
            accessibleGroups.isEmpty() -> 0 to emptyList()
            else -> 1 to accessibleGroups
        }

        // Step 1: DB — lightweight SELECT NEW + keyword/scope push-down
        var all = repository.listSummaries(keyword, scopeFilter, appGroups)

        // Step 2: memory — schemaType exact comma-split match (cannot be expressed cleanly in JPQL)
        if (!schemaType.isNullOrBlank()) {
            val target = schemaType.uppercase()
            all = all.filter { entity ->
                entity.schemaType.split(',').map { it.trim() }.contains(target)
            }
        }

        // Step 3: memory pagination — identical semantics to the legacy list API
        return all.toPage(pageable)
    }

    /** Lookup by the stable schema reference name used in `$ref`. */
    fun getByName(schemaName: String): WfSchema? {
        return repository.findBySchemaName(schemaName).orElse(null)
    }

    /**
     * Insert a new schema. Validates that all `schema:` references inside the payload
     * point to already-existing schemas (self-reference is not allowed during CREATE
     * because the schema is not yet saved).
     */
    fun save(entity: WfSchema): WfSchema {
        validateSchemaReferences(entity.schemaJson, null)
        val saved = repository.save(entity)
        log.info { "Saved schema [${saved.schemaName}]" }
        publishToWorkers(saved)
        return saved
    }

    /**
     * Update an existing schema by name. Enforces frozen-state gating, validates references
     * (self-reference is allowed on UPDATE since the row already exists), then persists and
     * publishes the delta to workers.
     */
    @Transactional
    fun update(schemaName: String, entity: WfSchema): WfSchema {
        val existing = repository.findBySchemaName(schemaName)
            .orElseThrow { IllegalArgumentException("Schema not found: `$schemaName") }

        val canUnlock = hasPermission(SCHEMA_UNLOCK_PERMISSION)
        if (existing.frozen && !canUnlock) {
            throw AccessDeniedException("Schema [$schemaName] is frozen, current user has no permission to edit")
        }

        validateSchemaReferences(entity.schemaJson, schemaName)

        existing.schemaType = entity.schemaType
        existing.schemaFormat = entity.schemaFormat
        existing.schemaJson = entity.schemaJson
        existing.description = entity.description
        existing.scope = entity.scope
        existing.appGroup = entity.appGroup
        // Only unlock-privileged callers are allowed to mutate frozen state
        if (canUnlock) {
            existing.frozen = entity.frozen
        }

        val saved = repository.save(existing)
        log.info { "Updated schema [$schemaName], frozen=${saved.frozen}" }
        publishToWorkers(saved)
        return saved
    }

    /**
     * Delete a schema by name. Enforces the frozen-state gate, blocks deletion when the
     * schema is still referenced by another schema or any workflow input/output schema,
     * then unpublishes from workers.
     */
    @Transactional
    fun delete(schemaName: String) {
        val entity = repository.findBySchemaName(schemaName)
            .orElseThrow { IllegalArgumentException("Schema not found: $schemaName") }

        if (entity.frozen && !hasPermission(SCHEMA_UNLOCK_PERMISSION)) {
            throw AccessDeniedException("Schema [$schemaName] is frozen, current user has no permission to delete")
        }

        val referrers = findReferrers(schemaName)
        if (referrers.isNotEmpty()) {
            throw IllegalArgumentException(
                "Schema [$schemaName] is still referenced and cannot be deleted. Referrers: ${referrers.joinToString(", ")}"
            )
        }

        repository.delete(entity)
        log.info { "Deleted schema [$schemaName]" }
        unpublishFromWorkers(schemaName)
    }

    /** Extract every distinct `schema:<name>` target referenced inside a JSON Schema payload. */
    fun extractReferencedSchemaNames(schemaJson: String): Set<String> {
        return SCHEMA_REF_REGEX.findAll(schemaJson)
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * Validates that every `schema:` reference inside `schemaJson` points to an already
     * existing schema. [selfName] — when non-null — allows self-references (used on UPDATE
     * where the current schema already has a row).
     *
     * @throws IllegalArgumentException if any referenced target schema does not exist.
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
                "Schema references non-existing target Schema: ${invalidRefs.joinToString(", ")}. " +
                    "Please create these Schema first, or fix the reference relations."
            )
        }
    }

    /**
     * Returns all sources that reference [schemaName] — either via another schema's
     * `schemaJson` or via any workflow definition's input/output schemas.
     */
    private fun findReferrers(schemaName: String): List<String> {
        val referrers = mutableListOf<String>()

        // 1. Scan other schemas using exact regex (avoids false-positive json-schema: prefix matches)
        repository.findAll().forEach { other ->
            if (other.schemaName != schemaName &&
                containsReferenceTo(other.schemaJson.orEmpty(), schemaName)
            ) {
                referrers += "Schema[${other.schemaName}]"
            }
        }

        // 2. Scan every workflow definition's input/output schema columns
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
     * Returns historical versions for a schema. Current implementation is 1-version-only
     * (no row-level versioning yet); returns the current entity as the sole entry.
     */
    fun findVersions(schemaName: String): List<WfSchema> {
        val entity = repository.findBySchemaName(schemaName).orElse(null) ?: return emptyList()
        return listOf(entity)
    }

    // ===== Schema config distribution =====

    /** Full snapshot pull used by Worker instances during HTTP bootstrap. */
    fun loadAllSchemaSnapshots(): List<SchemaConfigSnapshot> {
        return repository.findAll().map { toSnapshot(it) }
    }

    /** Single-schema snapshot pull for incremental refresh. */
    fun getSnapshot(schemaName: String): SchemaConfigSnapshot? {
        val entity = repository.findBySchemaName(schemaName).orElse(null) ?: return null
        return toSnapshot(entity)
    }

    /** Fire-and-forget push of a schema snapshot to every connected worker. */
    private fun publishToWorkers(entity: WfSchema) {
        val publisher = schemaConfigPublisher ?: return
        asyncScope.launch {
            try {
                val snapshot = toSnapshot(entity)
                publisher.publish(snapshot)
            } catch (e: Exception) {
                log.error(e) { "Failed to publish schema [${entity.schemaName}] to workers" }
            }
        }
    }

    /** Fire-and-forget removal of a schema from every connected worker. */
    private fun unpublishFromWorkers(schemaName: String) {
        val publisher = schemaConfigPublisher ?: return
        asyncScope.launch {
            try {
                publisher.unpublish(schemaName)
            } catch (e: Exception) {
                log.error(e) { "Failed to unpublish schema [$schemaName] from workers" }
            }
        }
    }

    /** Convert a WfSchema entity to the wire-format snapshot expected by the SPI. */
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
