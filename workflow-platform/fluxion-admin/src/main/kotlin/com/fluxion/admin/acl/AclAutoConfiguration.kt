package com.fluxion.admin.acl

import com.fluxion.acl.spi.AuthProvider
import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * ACL 自动配置
 *
 * 根据 fluxion.acl.* 配置项装配对应的 AuthProvider 实现：
 * - fluxion.acl.enabled=false：全放行（NoopAuthProvider）
 * - fluxion.acl.provider=local：本地 JWT + DB（LocalAclProvider，默认）
 * - fluxion.acl.provider=remote：远程 ACL 服务（RemoteAclClient）
 * - fluxion.acl.disabled-management：禁用的本地管理项列表
 */
@Configuration
class AclAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${fluxion.acl.disabled-management:}")
    private lateinit var disabledManagement: String

    /**
     * 检查某个管理功能是否被禁用
     *
     * @param feature 功能名：user / role / permission / tenant / group
     * @return true 表示该功能被禁用，应返回 403
     */
    fun isManagementDisabled(feature: String): Boolean {
        val disabled = disabledManagement.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return disabled.contains(feature.lowercase())
    }

    /**
     * NoopAuthProvider — 全放行模式（fluxion.acl.enabled=false 时使用）
     *
     * 当 fluxion.acl.enabled=false 时，此 Bean 会被装配，
     * 替代 LocalAclProvider 或 RemoteAclClient
     */
    @Component
    @ConditionalOnProperty(name = ["fluxion.acl.enabled"], havingValue = "false")
    class NoopAuthProvider : AuthProvider {
        private val log = LoggerFactory.getLogger(javaClass)

        init {
            log.warn("ACL is disabled - all requests will be allowed without authentication")
        }

        override fun authenticate(username: String, password: String): AuthResult {
            return AuthResult(
                success = true,
                username = username,
                roles = listOf("ADMIN"),
                permissions = listOf("*")
            )
        }

        override fun validateToken(token: String): TokenClaims? {
            return TokenClaims(
                username = "anonymous",
                roles = listOf("ADMIN"),
                permissions = listOf("*"),
                tenant = null
            )
        }

        override fun issueToken(username: String, authorities: List<String>, tenant: String?): String {
            return "noop-token"
        }

        override fun hasPermission(username: String, tenant: String?, permission: String): Boolean {
            return true
        }
    }
}
