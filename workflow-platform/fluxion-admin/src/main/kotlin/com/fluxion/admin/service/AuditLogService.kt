package com.fluxion.admin.service

import com.fluxion.admin.entity.AuditLog
import com.fluxion.admin.repository.AuditLogRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Append-only audit trail service.
 *
 * Reads use JPA [Specification] to dynamically build WHERE clauses from five optional
 * filters (operator / action / resourceType / startTime / endTime) so the admin audit UI
 * can mix-and-match without requiring a family of overloaded repository methods.
 *
 * Collaborates with: AuditLogRepository (both standard `JpaRepository` and
 * `JpaSpecificationExecutor`), controllers that mutate admin resources (each writer is
 * responsible for producing an AuditLog row).
 */
@Service
class AuditLogService(private val repository: AuditLogRepository) {

    /**
     * Dynamic query page used by the admin audit list view.
     *
     * Blank filter values are intentionally skipped rather than coerced, so the UI can
     * send empty form fields without polluting the SQL predicate list.
     */
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

    /** Write a single audit entry. Idempotent via entity id (new rows always have id = 0). */
    fun save(entity: AuditLog): AuditLog {
        return repository.save(entity)
    }
}
