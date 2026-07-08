package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

/**
 * Base mapped superclass for all JPA entities with a numeric auto-increment primary key.
 *
 * Provides standard audit timestamp fields (`created_at`, `updated_at`) and auto-updates
 * `updatedAt` on every entity flush. All business entities using a numeric `id` as their
 * primary key should inherit from this class.
 *
 * Collaborates with: Flyway (DDL generation via `id` BIGINT PK convention), Spring Data JPA
 * repositories (`JpaRepository<T, Long>`).
 */
@MappedSuperclass
abstract class BaseEntity {

    /** Auto-increment numeric primary key, maps to column `id`. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    /** Entity creation timestamp, maps to column `created_at`. */
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** Entity last-update timestamp, maps to column `updated_at`. Auto-refreshed via @PreUpdate. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
