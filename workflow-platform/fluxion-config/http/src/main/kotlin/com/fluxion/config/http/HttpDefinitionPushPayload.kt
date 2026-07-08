package com.fluxion.config.http

/**
 * HTTP 宸ヤ綔娴佸畾涔夋帹閫佺殑 payload 缁撴瀯锛圓dmin 鈫?Worker锛?
 */
data class HttpDefinitionPushPayload(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val removed: Boolean
)
