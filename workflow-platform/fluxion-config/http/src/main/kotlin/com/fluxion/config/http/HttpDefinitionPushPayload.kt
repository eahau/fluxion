package com.fluxion.config.http

/**
 * HTTP workflow definition push payload structure (Admin → Worker).
 */
data class HttpDefinitionPushPayload(
    val workflowId: String,
    val definitionJson: String?,
    val version: Int,
    val removed: Boolean
)
