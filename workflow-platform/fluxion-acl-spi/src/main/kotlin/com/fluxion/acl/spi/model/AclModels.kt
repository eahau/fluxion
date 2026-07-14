package com.fluxion.acl.spi.model

/**
 * Authentication / authorization data models used by the ACL SPI layer.
 *
 * These classes intentionally have zero framework dependencies, so they can
 * flow across Admin (control plane) and Runtime (data plane) boundaries
 * without bringing Spring or database types with them.
 */

/**
 * Result of an authentication attempt returned by [com.fluxion.acl.spi.AuthProvider.authenticate].
 *
 * @param success       Whether authentication succeeded
 * @param username      Authenticated username (only when success=true)
 * @param roles         Role identifiers assigned to the user
 * @param permissions   Fine-grained permission strings assigned to the user
 * @param tenant        Optional tenant / organization identifier for multi-tenant deployments
 * @param errorMessage  Human-readable failure reason (only when success=false)
 */
data class AuthResult(
    val success: Boolean,
    val username: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val tenant: String? = null,
    val errorMessage: String? = null
)

/**
 * Verified claims extracted from a validated security token.
 *
 * Returned by [com.fluxion.acl.spi.AuthProvider.validateToken] once a bearer
 * token has been cryptographically verified. Downstream code (controllers,
 * workflow functions) read these claims to perform authorization checks.
 *
 * @param username    Authenticated subject username
 * @param roles       Role identifiers the subject holds
 * @param permissions Fine-grained permission strings the subject holds
 * @param tenant      Optional tenant / organization identifier
 */
data class TokenClaims(
    val username: String,
    val roles: List<String>,
    val permissions: List<String>,
    val tenant: String?
)
