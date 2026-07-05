package com.fluxion.admin.acl

import com.fluxion.acl.spi.AuthProvider
import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims
import com.fluxion.core.util.uncheckedCast
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

/**
 * 远程 ACL 客户端（OAuth 2.0 / OIDC 对齐）
 *
 * 调用外部 OAuth 2.0 / OIDC 兼容的身份服务，端点对齐标准规范：
 * - POST /oauth2/token          — OAuth 2.0 Token 端点（登录 / 签发）
 * - GET  /oauth2/userinfo       — OIDC UserInfo 端点（校验 Token + 获取用户信息）
 * - POST /oauth2/check          — Fluxion 扩展权限校验
 *
 * 可对接 Keycloak、Casdoor、Ory 等任何兼容 OAuth 2.0 / OIDC 的 IdP 服务。
 * 响应统一用 Map 解析，不定义 POJO，保持轻量。
 */
@Component
@ConditionalOnProperty(name = ["fluxion.acl.provider"], havingValue = "remote")
class RemoteAclClient(
    @Value("\${fluxion.acl.remote.base-url:http://acl-service:8080}")
    private val baseUrl: String
) : AuthProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    /**
     * OAuth 2.0 Resource Owner Password Credentials — 用户登录
     * POST /oauth2/token  grant_type=password
     */
    override fun authenticate(username: String, password: String): AuthResult {
        val url = "$baseUrl/oauth2/token"
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("username", username)
            add("password", password)
        }
        return try {
            val resp = restTemplate.postForObject(url, body, Map::class.java)
            val json = resp.uncheckedCast<Map<String, Any>>() ?: return AuthResult(success = false, errorMessage = "Empty response")
            val accessToken = json["access_token"] as? String
            if (accessToken != null) {
                AuthResult(
                    success = true,
                    username = username,
                    roles = json.stringList("roles"),
                    permissions = json.stringList("permissions"),
                    tenant = json["tenant"] as? String
                )
            } else {
                AuthResult(success = false, errorMessage = "Authentication failed: empty access_token")
            }
        } catch (e: Exception) {
            log.error("Remote ACL authenticate failed for user: $username", e)
            throw e
        }
    }

    /**
     * OIDC UserInfo 端点 — 校验 Token 并获取用户信息
     * GET /oauth2/userinfo  Authorization: Bearer {token}
     */
    override fun validateToken(token: String): TokenClaims? {
        val url = "$baseUrl/oauth2/userinfo"
        val headers = HttpHeaders().apply {
            setBearerAuth(token)
            accept = listOf(MediaType.APPLICATION_JSON)
        }
        return try {
            val resp = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<String>(headers), Map::class.java)
            val json = resp.body.uncheckedCast<Map<String, Any>>() ?: return null
            val sub = json["sub"] as? String ?: return null
            TokenClaims(
                username = sub,
                roles = json.stringList("roles"),
                permissions = json.stringList("permissions"),
                tenant = json["tenant"] as? String
            )
        } catch (e: Exception) {
            log.warn("Remote ACL validateToken failed: ${e.message}")
            throw e
        }
    }

    /**
     * OAuth 2.0 Client Credentials — 服务间签发 Token
     * POST /oauth2/token  grant_type=client_credentials
     */
    override fun issueToken(username: String, authorities: List<String>, tenant: String?): String {
        val url = "$baseUrl/oauth2/token"
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("username", username)
            add("scope", authorities.joinToString(" "))
            tenant?.let { add("tenant", it) }
        }
        return try {
            val resp = restTemplate.postForObject(url, body, Map::class.java)
            resp.uncheckedCast<Map<String, Any>>()?.get("access_token") as? String
                ?: throw RuntimeException("Failed to issue token: empty access_token")
        } catch (e: Exception) {
            log.error("Remote ACL issueToken failed for user: $username", e)
            throw e
        }
    }

    /**
     * Fluxion 扩展权限校验
     * POST /oauth2/check
     */
    override fun hasPermission(username: String, tenant: String?, permission: String): Boolean {
        val url = "$baseUrl/oauth2/check"
        val request = mapOf(
            "username" to username,
            "tenant" to tenant,
            "permission" to permission
        )
        return try {
            val result = restTemplate.postForObject(url, request, Map::class.java).uncheckedCast<Map<String, Any>>()
            result?.get("allowed") as? Boolean ?: false
        } catch (e: Exception) {
            log.warn("Remote ACL hasPermission failed for user: $username", e)
            throw e
        }
    }

    // ============ Map 扩展函数 ============

    private fun Map<String, Any>.stringList(key: String): List<String> =
        this[key].uncheckedCast<List<*>>()?.filterIsInstance<String>() ?: emptyList()
}
