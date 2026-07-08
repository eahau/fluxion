package com.fluxion.admin.acl

import com.fluxion.acl.spi.AuthProvider
import com.fluxion.acl.spi.model.AuthResult
import com.fluxion.acl.spi.model.TokenClaims
import com.fluxion.core.util.uncheckedCast
import org.slf4j.*
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
 * `AuthProvider` that delegates to an external OAuth 2.0 / OIDC-compatible IdP service.
 *
 * Endpoint contract (standard plus one Fluxion extension):
 *   - `POST /oauth2/token`   – OAuth 2.0 Token endpoint (login / service-to-service issuance).
 *   - `GET  /oauth2/userinfo` – OIDC UserInfo endpoint (token validation + identity).
 *   - `POST /oauth2/check`   – Fluxion extension: fine-grained permission check.
 *
 * Works out of the box with Keycloak, Casdoor, Ory Keto/Hydra, Auth0, etc. Responses are
 * parsed from generic `Map<String, Any>` payloads to keep the client dependency-free.
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
     * Authenticate with the Resource Owner Password Credentials flow.
     * `POST /oauth2/token` with `grant_type=password`.
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
            log.error(e) { "Remote ACL authenticate failed for user: `$username" }
            throw e
        }
    }

    /**
     * Validate token and load identity via the OIDC UserInfo endpoint.
     * `GET /oauth2/userinfo` with `Authorization: Bearer {token}`.
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
            log.warn(e) { "Remote ACL validateToken failed: ${e.message}" }
            throw e
        }
    }

    /**
     * Issue a service-to-service token using the Client Credentials flow.
     * `POST /oauth2/token` with `grant_type=client_credentials`.
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
            log.error(e) { "Remote ACL issueToken failed for user: `$username" }
            throw e
        }
    }

    /**
     * Fine-grained permission check (Fluxion-specific endpoint).
     * `POST /oauth2/check`.
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
            log.warn(e) { "Remote ACL hasPermission failed for user: `$username" }
            throw e
        }
    }

    // === Map extension helpers ==================================================================

    private fun Map<String, Any>.stringList(key: String): List<String> =
        this[key].uncheckedCast<List<*>>()?.filterIsInstance<String>() ?: emptyList()
}
