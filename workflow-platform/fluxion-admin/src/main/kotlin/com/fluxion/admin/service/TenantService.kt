package com.fluxion.admin.service

import com.fluxion.admin.entity.UserAppGroup
import com.fluxion.admin.repository.UserAppGroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Multi-tenant (app_group) membership and access-control service.
 *
 * Access rules:
 *   - ADMIN users implicitly have access to every app_group (no rows needed in this table)
 *   - Non-ADMIN users only see PRIVATE resources whose app_group is present in their
 *     user_app_groups rows
 *
 * Collaborates with: UserAppGroupRepository (persistence), SecurityContextHelper (uses
 * [getUserAppGroups] for scope-filter calculation), controllers (role/tenant admin UI).
 */
@Service
class TenantService(
    private val userAppGroupRepository: UserAppGroupRepository,
) {

    /**
     * Returns true when `username` is allowed to operate on `appGroup` resources.
     *
     * Short-circuits to true for ADMINs (implicit grant). Otherwise falls back to a
     * membership presence check in the join table.
     */
    fun hasAccess(username: String, appGroup: String, isAdmin: Boolean = false): Boolean {
        if (isAdmin) return true
        return userAppGroupRepository.existsByUsernameAndAppGroup(username, appGroup)
    }

    /** Return the (possibly empty) list of app_group identifiers visible to `username`. */
    fun getUserAppGroups(username: String): List<String> {
        return userAppGroupRepository.findByUsername(username).map { it.appGroup }
    }

    /**
     * Idempotently add a user to a single app_group.
     * No-op if the membership row already exists (avoids UK constraint violation).
     */
    @Transactional
    fun assignAppGroup(username: String, appGroup: String) {
        if (userAppGroupRepository.existsByUsernameAndAppGroup(username, appGroup)) return
        val entity = UserAppGroup().apply {
            this.username = username
            this.appGroup = appGroup
        }
        userAppGroupRepository.save(entity)
    }

    /** Batch add multiple group memberships for one user (per-entry idempotency). */
    @Transactional
    fun assignAppGroups(username: String, appGroups: List<String>) {
        appGroups.forEach { assignAppGroup(username, it) }
    }

    /** Remove one user↔group membership row. Safe even if the row does not exist. */
    @Transactional
    fun removeAppGroup(username: String, appGroup: String) {
        userAppGroupRepository.deleteByUsernameAndAppGroup(username, appGroup)
    }

    /** Return all users who belong to a given app_group (team listing). */
    fun getMembers(appGroup: String): List<String> {
        return userAppGroupRepository.findByAppGroup(appGroup).map { it.username }
    }
}
