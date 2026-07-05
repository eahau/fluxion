package com.fluxion.config.http

/**
 * HTTP 工作流定义推送的 payload 结构（Admin → Worker）
 */
data class HttpDefinitionPushPayload(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val removed: Boolean
)
