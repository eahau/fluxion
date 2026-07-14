package com.fluxion.admin.repository

import com.fluxion.admin.entity.SandboxInstance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

/**
 * Spring Data JPA repository for [SandboxInstance] debug-sandbox records.
 */
interface SandboxInstanceRepository : JpaRepository<SandboxInstance, Long> {

    /** All sandboxes belonging to one debug session (one per resource type). */
    fun findByOwnerSessionOrderById(ownerSession: String): List<SandboxInstance>

    /** All sandboxes for one app + session + lifecycle state. */
    fun findByAppIdAndOwnerSessionAndLifecycleIn(
        appId: Long, ownerSession: String, lifecycles: Collection<String>
    ): List<SandboxInstance>

    /** Scheduler sweep — every instance whose `expiredAt` has passed and is still not CLEANED. */
    @Query("SELECT s FROM SandboxInstance s WHERE s.expiredAt < :now AND s.lifecycle NOT IN ('CLEANED', 'FAILED') ORDER BY s.expiredAt ASC")
    fun findExpiredCandidates(now: LocalDateTime): List<SandboxInstance>

    /** Batch lifecycle transition used by acquire / release / cleanup. */
    @Modifying
    @Query("UPDATE SandboxInstance s SET s.lifecycle = :newLifecycle, s.lastUsedAt = :ts WHERE s.id = :id")
    fun updateLifecycle(id: Long, newLifecycle: String, ts: LocalDateTime): Int

    /** Owner-visible list for the "my active sandboxes" widget. */
    fun findByOwnerUsernameAndLifecycleIn(username: String, lifecycles: Collection<String>): List<SandboxInstance>
}
