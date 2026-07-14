package com.fluxion.admin.acl

import com.fluxion.admin.repository.UserRepository
import com.fluxion.admin.security.JwtTokenProvider
import com.fluxion.acl.spi.AuthProvider
import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims
import org.slf4j.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Default `AuthProvider` implementation backed by the local admin database.
 *
 * Authentication uses `UserDetailsService` + a `PasswordEncoder`; token issuance and
 * validation delegate to `JwtTokenProvider`. This is the provider used when
 * `fluxion.acl.provider=local` (or unset).
 */
@Component
@ConditionalOnProperty(name = ["fluxion.acl.provider"], havingValue = "local", matchIfMissing = true)
class LocalAclProvider(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userDetailsService: UserDetailsService,
    private val passwordEncoder: PasswordEncoder
) : AuthProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun authenticate(username: String, password: String): AuthResult {
        return try {
            val userDetails = userDetailsService.loadUserByUsername(username)

            if (!userDetails.isEnabled) {
                return AuthResult(success = false, errorMessage = "User is disabled")
            }

            if (!passwordEncoder.matches(password, userDetails.password)) {
                return AuthResult(success = false, errorMessage = "Invalid credentials")
            }

            val roles = userDetails.authorities
                .map { it.authority }
                .filter { it.startsWith("ROLE_") }
                .map { it.removePrefix("ROLE_") }

            val permissions = userDetails.authorities
                .map { it.authority }
                .filter { !it.startsWith("ROLE_") }

            AuthResult(
                success = true,
                username = username,
                roles = roles,
                permissions = permissions
            )
        } catch (e: UsernameNotFoundException) {
            log.warn { "User not found: `$username" }
            AuthResult(success = false, errorMessage = "User not found")
        } catch (e: Exception) {
            log.error(e) { "Authentication failed for user: `$username" }
            AuthResult(success = false, errorMessage = "Authentication failed")
        }
    }

    override fun validateToken(token: String): TokenClaims? {
        if (!jwtTokenProvider.validateToken(token)) {
            return null
        }

        val username = jwtTokenProvider.getUsername(token) ?: return null
        val authorities = jwtTokenProvider.getAuthorities(token)

        val roles = authorities.filter { it.startsWith("ROLE_") }.map { it.removePrefix("ROLE_") }
        val permissions = authorities.filter { !it.startsWith("ROLE_") }

        return TokenClaims(
            username = username,
            roles = roles,
            permissions = permissions,
            tenant = null
        )
    }

    override fun issueToken(username: String, authorities: List<String>, tenant: String?): String {
        return jwtTokenProvider.generateToken(username, authorities)
    }

    override fun hasPermission(username: String, tenant: String?, permission: String): Boolean {
        val user = userRepository.findByUsername(username) ?: return false

        return user.roles.any { role ->
            role.permissions.any { it.permission == permission }
        }
    }
}
