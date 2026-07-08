package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfDefinition
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

/**
 * Spring Data JPA repository for [WfDefinition] workflow DAG entities.
 *
 * Most queries mirror the route-conflict detection performed by WfDefinitionService:
 * protocol + (method) + bindKey uniqueness must be checked per scope (PLATFORM vs PRIVATE + appGroup)
 * to avoid two different workflows sharing the same external trigger address.
 */
interface WfDefinitionRepository : JpaRepository<WfDefinition, Long> {

    /** The standard lookup by stable business workflowId. */
    fun findByWorkflowId(workflowId: String): Optional<WfDefinition>

    /** Batch-load every definition in a given lifecycle state. */
    fun findByStatus(status: String): List<WfDefinition>

    /** Used by runtime route resolution when protocol + bindKey uniquely identify a workflow. */
    fun findByProtocolAndBindKey(protocol: String, bindKey: String): Optional<WfDefinition>

    /** Full uniqueness check for (protocol, method, bindKey) triple — duplicates detection. */
    fun findByProtocolAndMethodAndBindKey(protocol: String, method: String?, bindKey: String?): Optional<WfDefinition>

    /** Scoped uniqueness check for PLATFORM / MARKETPLACE scope (appGroup is null by convention). */
    fun findByScopeAndProtocolAndMethodAndBindKey(
        scope: String, protocol: String, method: String?, bindKey: String?
    ): Optional<WfDefinition>

    /** Scoped uniqueness check for PRIVATE tenant workflows. */
    fun findByScopeAndAppGroupAndProtocolAndMethodAndBindKey(
        scope: String, appGroup: String?, protocol: String, method: String?, bindKey: String?
    ): Optional<WfDefinition>

    /** Engine cold-start bootstrap: all ACTIVE PLATFORM workflows. */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE' AND d.scope = :scope")
    fun findAllActiveByScope(scope: String): List<WfDefinition>

    /** Engine cold-start bootstrap: ACTIVE PRIVATE workflows for one tenant. */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE' AND d.scope = :scope AND d.appGroup = :appGroup")
    fun findAllActiveByScopeAndAppGroup(scope: String, appGroup: String): List<WfDefinition>

    /** Engine cold-start bootstrap: every ACTIVE definition regardless of scope. */
    @Query("SELECT d FROM WfDefinition d WHERE d.status = 'ACTIVE'")
    fun findAllActive(): List<WfDefinition>
}
