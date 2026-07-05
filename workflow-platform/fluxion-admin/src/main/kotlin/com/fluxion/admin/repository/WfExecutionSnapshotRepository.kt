package com.fluxion.admin.repository

import com.fluxion.admin.entity.WfExecutionSnapshot
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface WfExecutionSnapshotRepository : JpaRepository<WfExecutionSnapshot, Long> {

    fun findByExecutionId(executionId: String): WfExecutionSnapshot?

    fun findByWorkflowId(workflowId: String, pageable: Pageable): Page<WfExecutionSnapshot>
}
