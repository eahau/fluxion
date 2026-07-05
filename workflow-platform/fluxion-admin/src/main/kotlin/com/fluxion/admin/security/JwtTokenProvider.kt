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
 * JWT 令牌签发与校验
 *
 * 使用 JJWT 0.12.x API：
 *   - 签名密钥由配置注入，建议生产环境通过环境变量设置
 *   - token 携带用户名、角色/权限声明
 */
@Component
class JwtTokenProvider(
    @Value("\${workflow.admin.jwt.secret:workflow-platform-default-secret-key-change-in-production}")
    private val secret: String,
    @Value("\${workflow.admin.jwt.expiration-ms:86400000}")
    private val expirationMs: Long
) {

    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun generateToken(authentication: Authentication): String {
        val principal = authentication.principal as UserDetails
        return generateToken(principal.username, principal.authorities.map { it.authority })
    }

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

    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getUsername(token: String): String? {
        return try {
            parseClaims(token).subject
        } catch (_: Exception) {
            null
        }
    }

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
