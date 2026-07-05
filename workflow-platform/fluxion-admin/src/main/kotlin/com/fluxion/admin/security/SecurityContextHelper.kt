package com.fluxion.admin.security

import com.fluxion.admin.service.TenantService
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 安全上下文辅助工具
 *
 * 从 Spring SecurityContext 中提取当前用户信息，
 * 并提供租户（app_group）权限校验方法。
 *
 * 权限规则：
 *   - ADMIN 角色用户隐式拥有所有 app_group 权限
 *   - 非 ADMIN 用户只能访问自己在 user_app_groups 中关联的 app_group
 *   - local / dev 环境下未认证时，视为 ADMIN（免鉴权调试）
 */
@Component
class SecurityContextHelper(
    private val tenantService: TenantService,
) {

    /**
     * 获取当前认证用户名。
     * 未认证（local 环境）时返回 "admin"。
     */
    fun currentUsername(): String {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return "admin"
        if (auth is AnonymousAuthenticationToken) return "admin"
        return auth.name
    }

    /**
     * 判断当前用户是否为 ADMIN 角色
     */
    fun isAdmin(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
            ?: return true  // 未认证 = local 环境 = ADMIN
        if (auth is AnonymousAuthenticationToken) return true
        return auth.authorities.any { it.authority == "ROLE_ADMIN" || it.authority == "ADMIN" }
    }

    /**
     * 校验当前用户是否有权访问指定 app_group
     *
     * @throws TenantAccessDeniedException 无权访问时抛出
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
     * 获取当前用户可访问的所有 app_group 列表。
     * ADMIN 返回 null（表示不限制）。
     */
    fun accessibleAppGroups(): List<String>? {
        if (isAdmin()) return null
        return tenantService.getUserAppGroups(currentUsername())
    }
}

/**
 * 租户访问被拒绝异常
 */
class TenantAccessDeniedException(
    val username: String,
    val appGroup: String,
) : RuntimeException("User [$username] does not have access to app_group [$appGroup]")
