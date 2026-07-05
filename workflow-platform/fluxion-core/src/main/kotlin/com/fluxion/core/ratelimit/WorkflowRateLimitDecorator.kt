package com.fluxion.core.ratelimit

import com.fluxion.core.decorator.WorkflowDecorator
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
 * 工作流级限流装饰器。
 *
 * 对整个工作流的执行频率进行限制，适用于 API 入口级治理场景
 *（如「每分钟最多 1000 次创建订单」）。
 *
 * 与函数级 [com.fluxion.decorator.impl.RateLimitDecorator] 的区别：
 * - 函数级限制单个节点的调用频率；
 * - 工作流级限制整段工作流被触发的频率。
 *
 * 配置参数（通过 [WorkflowDefinition.workflowDecoratorParams] 的 "workflow:ratelimit" 键）：
 * - `upLimited`:     窗口内最大请求数，默认 6
 * - `cdSeconds`:     恢复周期（秒），默认 10
 * - `recoveryPerCd`: 每个周期恢复的令牌数，默认等于 upLimited
 * - `rateLimitKey`:  显式限流键，优先级最高
 * - `keyPrefix`:     键前缀，默认 "fluxion:ratelimit:workflow:"
 *
 * 限流键生成优先级：rateLimitKey > 默认 "{prefix}{workflowId}"
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
