package com.fluxion.admin.repository

import com.fluxion.admin.entity.UserAppGroup
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [UserAppGroup] membership rows.
 *
 * Powers the multi-tenant scope filter in SecurityContextHelper: list the app groups a
 * given user can see, or verify a specific membership before allowing a PRIVATE write.
 */
interface UserAppGroupRepository : JpaRepository<UserAppGroup, Long> {

    /** Return every app-group membership row for the given username. */
    fun findByUsername(username: String): List<UserAppGroup>

    /** Return every user belonging to a specific app group (team listing). */
    fun findByAppGroup(appGroup: String): List<UserAppGroup>

    /** Presence check used for authorize-before-write gating. */
    fun existsByUsernameAndAppGroup(username: String, appGroup: String): Boolean

    /** Remove a single user↔app_group membership row. */
    fun deleteByUsernameAndAppGroup(username: String, appGroup: String)

    /** Remove every membership row for a user (invoked on user deletion). */
    fun deleteByUsername(username: String)
}
