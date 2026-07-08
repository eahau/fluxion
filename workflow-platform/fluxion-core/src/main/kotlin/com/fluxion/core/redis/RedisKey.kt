package com.fluxion.core.redis

import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowNode

/**
 * Redis key formatter for Fluxion engine data structures.
 *
 * Produces keys of the form `{appName:domain}:businessKey`.  The curly
 * braces form a Redis Cluster *hash tag* so that all keys sharing the
 * same `(appName, domain)` pair land on the same shard — this is what
 * makes multi-key operations (stream XREADGROUP with consumer groups,
 * Lua scripts that read a lock and a rate-limit counter, etc.) safe in
 * clustered deployments.
 *
 * `appName` defaults to the appGroup declared on [WorkflowDefinition]
 * or the static string `"fluxion"`; `domain` is usually a workflow id
 * or a sub-system identifier.  `businessKey` is the per-instance
 * discriminator (idempotency key, lock key, node id, ...).
 */
object RedisKey {

    private const val DEFAULT_APP_NAME = "fluxion"

    /**
     * Format a hashed Redis key.
     *
     * @param appName     app/tenant group; blank falls back to `"fluxion"`.
     * @param domain      sub-system identifier (workflowId, subsystem); blank → `"default"`.
     * @param businessKey per-instance discriminator.
     */
    @JvmStatic
    fun format(appName: String?, domain: String, businessKey: String): String {
        val app = appName?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_NAME
        val dom = domain.takeIf { it.isNotBlank() } ?: "default"
        val biz = businessKey.takeIf { it.isNotBlank() } ?: ""
        return "{$app:$dom}:$biz"
    }

    /** Convenience overload that sources appGroup/workflowId from a [WorkflowNode]. */
    @JvmStatic
    fun fromNode(node: WorkflowNode, businessKey: String): String =
        format(node.appGroup, node.workflowId, businessKey)

    /** Convenience overload that sources appGroup/workflowId from a [NodeInput]'s metadata. */
    @JvmStatic
    fun fromInput(input: NodeInput, businessKey: String): String =
        format(input.meta?.appGroup, input.meta?.workflowId ?: "", businessKey)

    /**
     * True iff `key` already has a hash tag of the form `{...}:...`.
     *
     * When true, callers should pass the key through unchanged so that
     * admin-supplied cache / rate-limit keys retain their user-chosen
     * shard affinity.
     */
    @JvmStatic
    fun hasHashTag(key: String): Boolean =
        key.startsWith("{") && key.contains("}:")

    /**
     * Wrap a user-supplied key with a hash tag if it does not already
     * carry one.  Idempotent — keys that already look tagged are
     * returned verbatim.
     */
    @JvmStatic
    fun wrapIfNeeded(appName: String?, domain: String, key: String): String {
        if (hasHashTag(key)) return key
        return format(appName, domain, key)
    }
}
