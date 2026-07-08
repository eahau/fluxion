package com.fluxion.decorator.ratelimit

import com.fluxion.decorator.decorator.WorkflowDecorator
import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.model.intParam
import com.fluxion.core.model.stringParam
import com.fluxion.core.redis.RedisKey
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Workflow-level rate limit decorator.
 *
 * Intended as a coarse admission-control / API-protection layer on a publicly
 * exposed endpoint: it enforces a global sliding-window bucket per workflow id
 * (or shared custom key) before the DAG is even materialised, keeping the
 * per-node decorator available for fine-grained function-level protection.
 *
 * ### Config (key `workflow:ratelimit` in `WorkflowDefinition.workflowDecoratorParams`)
 *
 * | Key              | Description                                       | Default                          |
 * |------------------|---------------------------------------------------|----------------------------------|
 * | `upLimited`      | Max permits in the sliding window                 | 6                                |
 * | `cdSeconds`      | Cooldown window length (seconds)                  | 10                               |
 * | `recoveryPerCd`  | Permits restored per `cdSeconds`                  | equals `upLimited`               |
 * | `rateLimitKey`   | Explicit shared bucket key                        | `{prefix}{workflowId}`           |
 * | `keyPrefix`      | Prepended to generated business key               | `fluxion:ratelimit:workflow:`    |
 *
 * Key resolution: explicit `rateLimitKey` (wrapped by `wrapIfNeeded`) wins,
 * otherwise the generated prefix+workflow id.
 */
class WorkflowRateLimitDecorator(
    private val store: RateLimitStore
) : WorkflowDecorator {

    override fun name(): String = "workflow:ratelimit"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val params = def.decoratorParams(name())
        val config = resolveConfig(params)
        val key = resolveKey(def, params)

        // Dispatch to IO because the store may talk to Redis over the network;
        // workflow callers are typically on a Netty / servlet event-loop thread.
        val allowed = withContext(Dispatchers.IO) { store.tryAcquire(key, config) }
        if (!allowed) {
            throw RateLimitExceededException(
                "Workflow [${def.id}] rate limit exceeded (upLimited=${config.upLimited}, cdSeconds=${config.cdSeconds})"
            )
        }
        return execute()
    }

    private fun resolveConfig(params: Map<String, Any>?): RateLimitConfig {
        val upLimited = params.intParam("upLimited", RateLimitConfig.DEFAULT_UP_LIMITED)
        val cdSeconds = params.intParam("cdSeconds", RateLimitConfig.DEFAULT_CD_SECONDS)
        val recoveryPerCd = params.intParam("recoveryPerCd", upLimited)
        return RateLimitConfig(upLimited, cdSeconds, recoveryPerCd)
    }

    private fun resolveKey(def: WorkflowDefinition, params: Map<String, Any>?): String {
        val explicit = params.stringParam("rateLimitKey")
        if (!explicit.isNullOrBlank()) {
            return RedisKey.wrapIfNeeded(def.appGroup, def.id, explicit)
        }
        val prefix = params.stringParam("keyPrefix") ?: "fluxion:ratelimit:workflow:"
        return RedisKey.format(def.appGroup, def.id, "$prefix${def.id}")
    }
}
