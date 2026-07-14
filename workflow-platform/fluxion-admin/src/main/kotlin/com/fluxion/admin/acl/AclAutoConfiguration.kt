package com.fluxion.admin.acl

import com.fluxion.acl.spi.AuthProvider
import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Bootstraps the pluggable `AuthProvider` SPI based on the `fluxion.acl.*` configuration.
 *
 * Provider selection:
 *   - `fluxion.acl.enabled=false`        → `NoopAuthProvider` (allow everything, dev-only).
 *   - `fluxion.acl.provider=local`       → `LocalAclProvider` (default, DB-backed + JWT).
 *   - `fluxion.acl.provider=remote`      → `RemoteAclClient` (OAuth/OIDC IdP over HTTP).
 *
 * The `disabled-management` list lets operators hide specific CRUD pages in the admin UI
 * (user / role / permission / tenant / group) when those are managed externally.
 */
@Configuration
class AclAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${fluxion.acl.disabled-management:}")
    private lateinit var disabledManagement: String

    /**
     * Query whether a specific management feature is hidden for this deployment.
     *
     * @param feature feature name: `user` / `role` / `permission` / `tenant` / `group`
     * @return `true` if the UI + API should return 403 for this management surface.
     */
    fun isManagementDisabled(feature: String): Boolean {
        val disabled = disabledManagement.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return disabled.contains(feature.lowercase())
    }

    /**
     * Pass-through `AuthProvider` used when `fluxion.acl.enabled=false`.
     *
     * Every token, credential and permission check is accepted; install **only** in
     * local/demo environments.
     */
    @Component
    @ConditionalOnProperty(name = ["fluxion.acl.enabled"], havingValue = "false")
    class NoopAuthProvider : AuthProvider {
        private val log = LoggerFactory.getLogger(javaClass)

        init {
            log.warn { "ACL is disabled - all requests will be allowed without authentication" }
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
