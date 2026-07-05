package com.fluxion.acl.spi

import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims

/**
 * 认证与授权 SPI
 *
 * 默认实现：LocalAclProvider（本地 JWT + DB）
 * 远程实现：RemoteAclClient（远程 ACL 服务，降级用缓存）
 */
interface AuthProvider {
    /** 认证：验证凭证，返回认证结果 */
    fun authenticate(username: String, password: String): AuthResult

    /** 校验 Token，返回 Token 声明（用户名、角色、租户等） */
    fun validateToken(token: String): TokenClaims?

    /** 签发 Token */
    fun issueToken(username: String, authorities: List<String>, tenant: String? = null): String

    /** 权限校验 */
    fun hasPermission(username: String, tenant: String?, permission: String): Boolean
}
