package com.fluxion.redis

import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowNode

object RedisKey {

    private const val DEFAULT_APP_NAME = "fluxion"

    @JvmStatic
    fun format(appName: String?, domain: String, businessKey: String): String {
        val app = appName?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_NAME
        val dom = domain.takeIf { it.isNotBlank() } ?: "default"
        val biz = businessKey.takeIf { it.isNotBlank() } ?: ""
        return "{$app:$dom}:$biz"
    }

    @JvmStatic
    fun fromNode(node: WorkflowNode, businessKey: String): String =
        format(node.appGroup, node.workflowId, businessKey)

    @JvmStatic
    fun fromInput(input: NodeInput, businessKey: String): String =
        format(input.meta?.appGroup, input.meta?.workflowId ?: "", businessKey)

    @JvmStatic
    fun hasHashTag(key: String): Boolean =
        key.startsWith("{") && key.contains("}:")

    @JvmStatic
    fun wrapIfNeeded(appName: String?, domain: String, key: String): String {
        if (hasHashTag(key)) return key
        return format(appName, domain, key)
    }
}