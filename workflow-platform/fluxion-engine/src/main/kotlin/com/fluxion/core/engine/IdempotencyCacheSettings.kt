package com.fluxion.core.engine

/**
 * Idempotency cache configuration model.
 *
 * Global defaults (ttl, max size, custom Caffeine spec) can be overridden
 * per-workflow via the [workflows] map. The effective settings for a given
 * workflow are resolved through [effective].
 *
 * Mirrors the shape of `IdempotencyConfig` exposed by the adapter SPI layer
 * but is declared here so the engine core can use it without pulling in
 * adapter dependencies.
 */
data class IdempotencyCacheSettings(
    val enabled: Boolean = true,
    val ttlHours: Long = 24,
    val maxSize: Long = 1_000,
    val spec: String? = null,
    val workflows: Map<String, WorkflowIdempotencyCacheSettings> = emptyMap()
) {
    /**
     * Resolve effective settings for a specific workflow ID.
     *
     * Per-workflow overrides take precedence on a field-by-field basis;
     * `null` fields on the per-workflow entry fall back to the global
     * values from this instance.
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
 * Per-workflow idempotency cache overrides.
 *
 * Any field left `null` inherits from the parent
 * [IdempotencyCacheSettings] during resolution.
 */
data class WorkflowIdempotencyCacheSettings(
    val enabled: Boolean? = null,
    val ttlHours: Long? = null,
    val maxSize: Long? = null,
    val spec: String? = null
)

/**
 * Fully-resolved idempotency cache settings (no nullable fields).
 *
 * Produced by [IdempotencyCacheSettings.effective] and consumed directly
 * by [CaffeineIdempotencyStore] to build/refresh individual cache instances.
 */
data class EffectiveIdempotencyCacheSettings(
    val enabled: Boolean,
    val ttlHours: Long,
    val maxSize: Long,
    val spec: String?
)
