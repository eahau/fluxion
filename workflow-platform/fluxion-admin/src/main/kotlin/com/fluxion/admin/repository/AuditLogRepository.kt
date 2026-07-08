package com.fluxion.admin.repository

import com.fluxion.admin.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/**
 * Spring Data JPA repository for append-only [AuditLog] rows.
 *
 * Extends `JpaSpecificationExecutor` because the admin audit list UI supports arbitrary
 * filter combinations (operator, resource type, date range, action) that are easier to
 * express via JPA Specifications than derived query methods.
 */
interface AuditLogRepository : JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog>
