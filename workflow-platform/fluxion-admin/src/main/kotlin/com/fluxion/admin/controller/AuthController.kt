package com.fluxion.admin.controller

import com.fluxion.acl.spi.AuthProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 认证接口
 *
 * 支持多种认证提供者（通过 AuthProvider SPI）：
 * - 本地数据库（默认，fluxion.acl.provider=local）
 * - 远程 ACL 服务（fluxion.acl.provider=remote）
 * - LDAP（企业内部统一账号，可扩展）
 * - OAuth2（第三方登录，可扩展）
 */
@RestController
class AuthController(
    private val authProvider: AuthProvider
) {

    @PostMapping("/api/admin/auth/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, String>> {
        val result = authProvider.authenticate(request.username, request.password)
        if (!result.success) {
            return ResponseEntity.status(401).build()
        }
        val token = authProvider.issueToken(result.username!!, result.permissions, result.tenant)
        return ResponseEntity.ok(mapOf("token" to token, "type" to "Bearer"))
    }

    data class LoginRequest(
        val username: String,
        val password: String
    )
}
