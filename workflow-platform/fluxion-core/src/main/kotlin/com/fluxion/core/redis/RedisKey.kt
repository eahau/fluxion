package com.fluxion.core.redis

import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowNode

/**
 * Redis Key 命名规范工具。
 *
 * 规范：{appName:domain}:businessKey
 * - appName：应用名（通常对应 WorkflowDefinition.appGroup）
 * - domain：域（通常对应 workflowId）
 * - businessKey：业务键
 *
 * 在 Redis 集群中，花括号内的 "appName:domain" 作为 hash tag，
 * 确保同一应用/工作流下的相关 Key 路由到同一个哈希槽。
 */
object RedisKey {

    private const val DEFAULT_APP_NAME = "fluxion"

    /**
     * 按规范格式化 Redis Key。
     *
     * @param appName 应用名，为空时使用默认值 "fluxion"
     * @param domain 域，为空时使用默认值 "default"
     * @param businessKey 业务键
     */
    @JvmStatic
    fun format(appName: String?, domain: String, businessKey: String): String {
        val app = appName?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_NAME
        val dom = domain.takeIf { it.isNotBlank() } ?: "default"
        val biz = businessKey.takeIf { it.isNotBlank() } ?: ""
        return "{$app:$dom}:$biz"
    }

    /**
     * 从 [WorkflowNode] 生成 Key：appName=node.appGroup, domain=node.workflowId。
     */
    @JvmStatic
    fun fromNode(node: WorkflowNode, businessKey: String): String =
        format(node.appGroup, node.workflowId, businessKey)

    /**
     * 从 [NodeInput] 生成 Key：appName=meta.appGroup, domain=meta.workflowId。
     */
    @JvmStatic
    fun fromInput(input: NodeInput, businessKey: String): String =
        format(input.meta?.appGroup, input.meta?.workflowId ?: "", businessKey)

    /**
     * 判断 key 是否已经包含集群 hash tag（形如 {...}:...）。
     *
     * 用于用户显式传入 Key 的场景：若已带 hash tag 则不再二次包装，
     * 避免破坏用户自定义的槽位路由。
     */
    @JvmStatic
    fun hasHashTag(key: String): Boolean =
        key.startsWith("{") && key.contains("}:")

    /**
     * 若 key 尚未包含 hash tag，则按规范包装；否则保持原样。
     *
     * 适用于 RedisCommandFunction、CacheFunctions 等接收用户配置 Key 的场景。
     */
    @JvmStatic
    fun wrapIfNeeded(appName: String?, domain: String, key: String): String {
        if (hasHashTag(key)) return key
        return format(appName, domain, key)
    }
}
