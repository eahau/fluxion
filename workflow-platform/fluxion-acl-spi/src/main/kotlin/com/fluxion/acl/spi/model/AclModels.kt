package com.fluxion.acl.spi.model

/**
 * 认证结果
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
 * Token 声明
 */
data class TokenClaims(
    val username: String,
    val roles: List<String>,
    val permissions: List<String>,
    val tenant: String?
)
