package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfExecutionSnapshot
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for immutable [WfExecutionSnapshot] records.
 *
 * Snapshots are written exactly once by the post-execution hook; reads are limited to
 * lookup by execution UUID (debug view) and paginated history per workflowId (timeline UI).
 */
interface WfExecutionSnapshotRepository : JpaRepository<WfExecutionSnapshot, Long> {

    /** Single-row lookup for the execution detail view. */
    fun findByExecutionId(executionId: String): WfExecutionSnapshot?

    /** Paginated history of all executions of a given workflow — newest-first via Pageable sort. */
    fun findByWorkflowId(workflowId: String, pageable: Pageable): Page<WfExecutionSnapshot>
}
