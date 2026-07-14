package com.fluxion.admin.controller

import com.fluxion.acl.spi.AuthProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Login endpoint controller.
 *
 * Authentication is fully delegated to the pluggable [AuthProvider] SPI — concrete
 * implementations are selected via the `fluxion.acl.provider` config key and cover:
 *   - LOCAL: JPA-backed username / password table (default)
 *   - REMOTE: HTTP callout to a standalone ACL microservice
 *   - LDAP / OAuth2: pluggable extensions wired outside this module
 *
 * On successful authenticate the provider issues a signed JWT (or opaque token,
 * depending on implementation) that the UI attaches as a `Bearer` header for
 * subsequent admin calls.
 *
 * Collaborates with: AuthProvider SPI only.
 */
@RestController
class AuthController(
    private val authProvider: AuthProvider
) {

    /**
     * Authenticate user credentials and issue a Bearer token on success.
     * Returns 401 Unauthorized when the provider reports a failed authentication;
     * returns the token + type=Bearer on success.
     */
    @PostMapping("/api/admin/auth/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, String>> {
        val result = authProvider.authenticate(request.username, request.password)
        if (!result.success) {
            return ResponseEntity.status(401).build()
        }
        val token = authProvider.issueToken(result.username!!, result.permissions, result.tenant)
        return ResponseEntity.ok(mapOf("token" to token, "type" to "Bearer"))
    }

    /** Plain username + password request DTO (kept inline — no generated spec for this endpoint yet). */
    data class LoginRequest(
        val username: String,
        val password: String
    )
}
