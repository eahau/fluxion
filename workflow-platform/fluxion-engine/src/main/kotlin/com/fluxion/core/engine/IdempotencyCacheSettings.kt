package com.fluxion.core.engine

data class IdempotencyCacheSettings(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val workflows: Map<String, WorkflowIdempotencyCacheSettings> = emptyMap()
) {
    fun effective(workflowId: String?): EffectiveIdempotencyCacheSettings {
        val wf = workflowId?.let(workflows::get)
        return EffectiveIdempotencyCacheSettings(
            enabled = wf?.enabled ?: enabled,
            ttlHours = wf?.ttlHours ?: ttlHours
        )
    }
}

data class WorkflowIdempotencyCacheSettings(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null
)

data class EffectiveIdempotencyCacheSettings(
    val enabled: Boolean,
    val ttlHours: Long
)