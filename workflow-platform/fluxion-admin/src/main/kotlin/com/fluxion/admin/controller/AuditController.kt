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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 审计日志 REST API
 */
@RestController
class AuditController(
    private val auditLogService: AuditLogService
) : AuditApi {

    override fun listAuditLogs(
        operator: String?,
        action: String?,
        resourceType: String?,
        startTime: java.time.OffsetDateTime?,
        endTime: java.time.OffsetDateTime?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseAuditLog> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = auditLogService.list(
            operator = operator,
            action = action,
            resourceType = resourceType,
            startTime = startTime?.toLocalDateTime(),
            endTime = endTime?.toLocalDateTime(),
            pageable = pageable
        )
        return ResponseEntity.ok(PageResponseAuditLog().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { toDto(it) }.toMutableList()
        })
    }

    private fun toDto(entity: AuditLog): AuditLogDto = AuditLogDto().apply {
        id = entity.id.toString()
        this.operator = entity.operator
        this.action = entity.action
        this.resourceType = entity.resourceType
        this.resourceId = entity.resourceId
        detail = entity.detail
        ip = entity.ip
        timestamp = entity.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
    }
}
