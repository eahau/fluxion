package com.fluxion.core.engine

/**
 * 幂等缓存运行时配置 — 应用级默认 + 工作流级覆盖的二维配置。
 *
 * 与配置中心传输对象 [com.fluxion.adapter.spi.config.IdempotencyConfig] 解耦，
 * 使 fluxion-core 不依赖 adapter-spi，保持零外部框架依赖。
 */
data class IdempotencyCacheSettings(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val maxSize: Long = 1_000,
    val spec: String? = null,
    val workflows: Map<String, WorkflowIdempotencyCacheSettings> = emptyMap()
) {
    /**
     * 获取指定工作流的有效配置。
     *
     * 工作流级非空字段覆盖应用级默认值；未指定工作流则使用应用级默认。
     */
    fun effective(workflowId: String?): EffectiveIdempotencyCacheSettings {
        val wf = workflowId?.let(workflows::get)
        return EffectiveIdempotencyCacheSettings(
            enabled = wf?.enabled ?: enabled,
            ttlHours = wf?.ttlHours ?: ttlHours,
            maxSize = wf?.maxSize ?: maxSize,
            spec = wf?.spec ?: spec
        )
    }
}

/**
 * 工作流级幂等缓存配置覆盖。
 *
 * 所有字段均可空，null 表示继承应用级默认值。
 */
data class WorkflowIdempotencyCacheSettings(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null,
    val maxSize: Long? = null,
    val spec: String? = null
)

/**
 * 合并后的有效幂等缓存配置 — 所有字段非空，可直接用于构建 Caffeine 缓存。
 */
data class EffectiveIdempotencyCacheSettings(
    val enabled: Boolean,
    val ttlHours: Long,
    val maxSize: Long,
    val spec: String?
)
