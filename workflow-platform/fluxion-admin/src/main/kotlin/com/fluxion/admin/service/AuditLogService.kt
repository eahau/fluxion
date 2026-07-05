package com.fluxion.admin.service

import com.fluxion.admin.entity.AuditLog
import com.fluxion.admin.repository.AuditLogRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AuditLogService(private val repository: AuditLogRepository) {

    fun list(
        operator: String?,
        action: String?,
        resourceType: String?,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        pageable: Pageable
    ): Page<AuditLog> {
        val spec = Specification<AuditLog> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            operator?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.like(root.get("operator"), "%$it%"))
            }
            action?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.equal(root.get<String>("action"), it))
            }
            resourceType?.takeIf { it.isNotBlank() }?.let {
                predicates.add(cb.equal(root.get<String>("resourceType"), it))
            }
            startTime?.let {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), it))
            }
            endTime?.let {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), it))
            }
            cb.and(*predicates.toTypedArray())
        }
        return repository.findAll(spec, pageable)
    }

    fun save(entity: AuditLog): AuditLog {
        return repository.save(entity)
    }
}
