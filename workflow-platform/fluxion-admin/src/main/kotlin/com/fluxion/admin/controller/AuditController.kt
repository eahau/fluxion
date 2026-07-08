package com.fluxion.admin.controller

import com.fluxion.admin.entity.AuditLog
import com.fluxion.admin.generated.api.AuditApi
import com.fluxion.admin.generated.model.AuditLog as AuditLogDto
import com.fluxion.admin.generated.model.PageResponseAuditLog
import com.fluxion.admin.service.AuditLogService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneId

/**
 * Append-only audit log viewer REST controller (implements OpenAPI-generated [AuditApi]).
 *
 * Supports five filter dimensions (operator / action / resourceType / startTime /
 * endTime); each optional filter is translated into a JPA Specification by
 * AuditLogService so the query stays efficient even for high-volume audit tables.
 *
 * Collaborates with: AuditLogService (spec-driven page query).
 */
@RestController
class AuditController(
    private val auditLogService: AuditLogService
) : AuditApi {

    /**
     * Paginated audit log listing.
     *
     * Timestamps arrive from the frontend as epoch-millis and are converted into
     * LocalDateTime using the system default zone before hitting the service layer.
     * Page size is capped at 100 to cap the server response.
     */
    override fun listAuditLogs(
        operator: String?,
        action: String?,
        resourceType: String?,
        startTime: Long?,
        endTime: Long?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseAuditLog> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = auditLogService.list(
            operator = operator,
            action = action,
            resourceType = resourceType,
            startTime = startTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
            endTime = endTime?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
            pageable = pageable
        )
        return ResponseEntity.ok(PageResponseAuditLog().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { toDto(it) }.toMutableList()
        })
    }

    /** Entity → OpenAPI DTO mapper. `createdAt` becomes timestamp-millis for the frontend. */
    private fun toDto(entity: AuditLog): AuditLogDto = AuditLogDto().apply {
        id = entity.id.toString()
        this.operator = entity.operator
        this.action = entity.action
        this.resourceType = entity.resourceType
        resourceId = entity.resourceId
        detail = entity.detail
        ip = entity.ip
        timestamp = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
