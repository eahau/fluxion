package com.fluxion.core.model

/**
 * 触发器类型
 */
enum class TriggerType {
    /** 手动执行（用户点击触发） */
    MANUAL,
    /** Webhook（HTTP POST 触发） */
    WEBHOOK,
    /** 应用事件（内置事件 / 第三方事件） */
    EVENT,
    /** API 调用（同步 HTTP 请求，返回工作流执行结果） */
    API
}

/**
 * 工作流触发器 — 定义工作流的启动方式
 *
 * 一个工作流可以有多个触发器，每个触发器定义一种启动方式。
 */
data class WorkflowTrigger(
    /** 触发器 ID（同一工作流内不重复） */
    val id: String = "",
    /** 触发器类型 */
    val type: TriggerType = TriggerType.MANUAL,
    /**
     * 触发器配置（按类型不同结构不同）：
     * - EVENT: { "eventType": "user.created", "source": "authing" }
     * - WEBHOOK: { "path": "/hooks/my-workflow" }
     * - API: { "path": "/api/workflows/{id}/execute" }
     * - MANUAL: null
     */
    val config: Map<String, Any>? = null,
    /** 是否启用 */
    val enabled: Boolean = true
)
