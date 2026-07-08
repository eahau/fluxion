package com.fluxion.admin.security

import com.fasterxml.jackson.core.type.TypeReference
import com.fluxion.core.util.JsonUtil
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

/**
 * Small wrapper around JJWT 0.12.x that issues and validates HS256-signed JWTs for the
 * admin console.
 *
 * Configuration:
 *  - `workflow.admin.jwt.secret`       – signing key (set via env vars in production).
 *  - `workflow.admin.jwt.expiration-ms` – token lifetime, default 24 hours.
 *
 * Each token bundles the username plus a flat list of Spring `authority` strings (roles
 * with the `ROLE_` prefix and raw permission names).
 */
@Component
class JwtTokenProvider(
    @Value("\${workflow.admin.jwt.secret:workflow-platform-default-secret-key-change-in-production}")
    private val secret: String,
    @Value("\${workflow.admin.jwt.expiration-ms:86400000}")
    private val expirationMs: Long
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    /** Issue a JWT from an authenticated Spring `Authentication`. */
    fun generateToken(authentication: Authentication): String {
        val principal = authentication.principal as UserDetails
        return generateToken(principal.username, principal.authorities.map { it.authority })
    }

    /** Issue a JWT from explicit username + authority list (used by ACL SPI). */
    fun generateToken(username: String, authorities: List<String>): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)
        return Jwts.builder()
            .subject(username)
            .claim(AUTHORITIES_CLAIM, authorities)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    /** Return `true` if the token is non-expired, signature-verified and well-formed. */
    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Extract the subject (username) from a valid token; `null` on parse failure. */
    fun getUsername(token: String): String? {
        return try {
            parseClaims(token).subject
        } catch (_: Exception) {
            null
        }
    }

    /** Extract the authority list from a valid token; `emptyList()` on parse failure. */
    fun getAuthorities(token: String): List<String> {
        return try {
            parseClaims(token)[AUTHORITIES_CLAIM]
                ?.let { JsonUtil.getMapper().convertValue(it, object : TypeReference<List<String>>() {}) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseClaims(token: String) =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    companion object {
        private const val AUTHORITIES_CLAIM = "authorities"
    }
}
