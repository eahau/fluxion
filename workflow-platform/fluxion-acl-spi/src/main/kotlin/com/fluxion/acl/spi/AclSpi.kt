package com.fluxion.acl.spi

import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims

/**
 * Authentication &amp; Authorization Service Provider Interface (SPI).
 *
 * Decouples the Fluxion platform from any specific identity provider so
 * deployments can plug in their preferred solution:
 * - Default (Local):  LocalAclProvider backed by JWT + admin database tables
 * - Remote:          RemoteAclClient calling a centralized ACL service with
 *                    local cache + graceful degradation fallback
 *
 * Implementations MUST be thread-safe because the same instance is shared
 * across every inbound HTTP/RPC/MQ request on the platform.
 */
interface AuthProvider {

    /**
     * Verify raw username/password credentials and return an [AuthResult]
     * describing the authenticated principal.
     *
     * Implementations should NEVER log or record raw passwords.
     *
     * @param username Plain-text login identifier
     * @param password Plain-text password (never stored or logged)
     * @return AuthResult describing success/failure and any granted authorities
     */
    fun authenticate(username: String, password: String): AuthResult

    /**
     * Cryptographically validate a bearer token and extract its claims.
     *
     * @param token Raw bearer token (JWT or opaque token handled by implementation)
     * @return Parsed claims if the token is valid; null if expired, malformed, or revoked
     */
    fun validateToken(token: String): TokenClaims?

    /**
     * Mint a new security token for the given principal.
     *
     * Called by login endpoints and service-to-service impersonation flows.
     *
     * @param username    Subject identifier
     * @param authorities Granted roles / permissions (serialized into token claims)
     * @param tenant      Optional tenant identifier for multi-tenant platforms
     * @return Serialized bearer token (typically a signed JWT)
     */
    fun issueToken(username: String, authorities: List<String>, tenant: String? = null): String

    /**
     * Authorization check: does the given subject hold a specific permission?
     *
     * Used by fine-grained access checks in workflow admin endpoints.
     *
     * @param username   Subject to check
     * @param tenant     Optional tenant scope for the permission check
     * @param permission Permission identifier (e.g. "workflow:definition:publish")
     * @return true if the permission is granted
     */
    fun hasPermission(username: String, tenant: String?, permission: String): Boolean
}
