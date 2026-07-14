package com.fluxion.admin.security

import com.fluxion.admin.service.TenantService
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Helpers that read the current Spring `SecurityContext` to expose the caller identity and
 * enforce multi-tenant (app-group) scoping.
 *
 * Access rules:
 *   - Users holding `ROLE_ADMIN` (or raw `ADMIN` authority) implicitly have access to every
 *     `app_group`.
 *   - Non-admin users may only access `app_group`s to which they are linked via the
 *     `user_app_groups` join table.
 *   - When the context is unauthenticated (typical local/dev with auth disabled) the helper
 *     treats the caller as `admin` with unrestricted access to ease testing.
 */
@Component
class SecurityContextHelper(
    private val tenantService: TenantService,
) {

    /**
     * Return the current authenticated username.
     * Falls back to `"admin"` in local/dev environments where no authentication is set up.
     */
    fun currentUsername(): String {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return "admin"
        if (auth is AnonymousAuthenticationToken) return "admin"
        return auth.name
    }

    /** Return `true` if the caller holds the ADMIN role (or auth is disabled). */
    fun isAdmin(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return true
        if (auth is AnonymousAuthenticationToken) return true
        return auth.authorities.any { it.authority == "ROLE_ADMIN" || it.authority == "ADMIN" }
    }

    /**
     * Verify that the current caller may access data owned by `appGroup`.
     *
     * @throws TenantAccessDeniedException if access is disallowed.
     */
    fun requireAppGroupAccess(appGroup: String) {
        if (appGroup.isBlank()) return
        val username = currentUsername()
        val admin = isAdmin()
        if (!tenantService.hasAccess(username, appGroup, admin)) {
            throw TenantAccessDeniedException(username, appGroup)
        }
    }

    /**
     * List every `app_group` the caller can see; `null` is returned for ADMIN callers and
     * means "no restriction" (callers should interpret accordingly).
     */
    fun accessibleAppGroups(): List<String>? {
        if (isAdmin()) return null
        return tenantService.getUserAppGroups(currentUsername())
    }
}

/**
 * Thrown when a user attempts to read or write data belonging to an app group they are
 * not authorized to access.
 */
class TenantAccessDeniedException(
    val username: String,
    val appGroup: String,
) : RuntimeException("User [$username] does not have access to app_group [$appGroup]")
