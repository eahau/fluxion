package com.fluxion.decorator.impl

import com.fluxion.core.decorator.AsyncCallback
import com.fluxion.core.decorator.AsyncCallbackContext
import com.fluxion.core.decorator.AsyncCallbackStatus
import com.fluxion.core.decorator.CacheStore
import com.fluxion.core.decorator.NodeDecorator
import com.fluxion.core.engine.TaskInterceptor
import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.*
import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.core.redis.RedisKey
import com.fluxion.core.value.FunctionResult
import com.fluxion.decorator.impl.ratelimit.LocalRateLimitStore
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.slf4j.*
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * metrics 装饰器 — Micrometer 计时 & 计数
 */
class MetricsDecorator(private val meterRegistry: MeterRegistry) : NodeDecorator {

    override fun name(): String = "metrics:micrometer"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
        WorkflowFunction { input ->
            val start = System.currentTimeMillis()
            val tags = Tags.of("workflow", node.workflowId, "node", node.id)
            try {
                val result = function.apply(input)
                meterRegistry.counter("workflow.node.success", tags).increment()
                Timer.builder("workflow.node.duration").tags(tags).register(meterRegistry)
                    .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
                result
            } catch (e: Exception) {
                meterRegistry.counter("workflow.node.error", tags.and("error", e.javaClass.simpleName)).increment()
                throw e
            }
        }
}

/**
 * rateLimit 装饰器 — 滑动窗口限流。
 *
 * 通过 [RateLimitStore] SPI 解耦底层存储：
 * - 无外部存储时，使用内置的 [LocalRateLimitStore]（单 JVM）
 * - 分布式场景下注入 Redis 等 [RateLimitStore] 实现
 *
 * 配置参数（通过 [WorkflowNode.decoratorParams] 的 "ratelimit:slidingWindow" 键）：
 * - `upLimited`:     窗口内最大请求数，默认 6
 * - `cdSeconds`:     恢复周期（秒），默认 10
 * - `recoveryPerCd`: 每个周期恢复的令牌数，默认等于 upLimited
 * - `rateLimitKey`:  显式限流键，优先级最高
 * - `keyPrefix`:     键前缀，默认 "fluxion:ratelimit:"
 */
class RateLimitDecorator(private val store: RateLimitStore = LocalRateLimitStore()) : NodeDecorator {

    override fun name(): String = "ratelimit:slidingWindow"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        val config = resolveConfig(node)
        val key = resolveKey(node)

        return WorkflowFunction { input ->
            if (!store.tryAcquire(key, config)) {
                throw RateLimitExceededException(
                    "Node [${node.name}] rate limit exceeded (upLimited=${config.upLimited}, cdSeconds=${config.cdSeconds})"
                )
            }
            function.apply(input)
        }
    }

    private fun resolveConfig(node: WorkflowNode): RateLimitConfig {
        val params = node.decoratorParams(name())
        val upLimited = params.intParam("upLimited", RateLimitConfig.DEFAULT_UP_LIMITED)
        val cdSeconds = params.intParam("cdSeconds", RateLimitConfig.DEFAULT_CD_SECONDS)
        val recoveryPerCd = params.intParam("recoveryPerCd", upLimited)
        return RateLimitConfig(upLimited, cdSeconds, recoveryPerCd)
    }

    private fun resolveKey(node: WorkflowNode): String {
        val params = node.decoratorParams(name())
        val explicit = params.stringParam("rateLimitKey")
        val businessKey = if (!explicit.isNullOrBlank()) explicit else "ratelimit:${node.id}"
        return RedisKey.fromNode(node, businessKey)
    }
}

/**
 * cache 装饰器 — L1 本地缓存
 */
class CacheDecorator(private val cacheStore: CacheStore) : NodeDecorator {

    private val parser = SpelExpressionParser()

    override fun name(): String = "cache:local"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
        WorkflowFunction { input ->
            val params = node.decoratorParams(name())
            val ttl = params.longParam("ttlSeconds", 60L)
            val keyExpr = params.stringParam("cacheKeyExpression") ?: "#input"
            val businessKey = "cache:${node.id}:${evalKey(keyExpr, input.directInput)}"
            val cacheKey = RedisKey.fromNode(node, businessKey)

            cacheStore.get(cacheKey)?.let { return@WorkflowFunction FunctionResult.success(it) }

            val result = function.apply(input)
            result.output?.let { cacheStore.put(cacheKey, it, ttl, TimeUnit.SECONDS) }
            result
        }

    private fun evalKey(expr: String?, input: Any?): String {
        if (expr.isNullOrBlank() || expr == "#input") {
            return input?.toString() ?: "null"
        }
        return try {
            val ctx = StandardEvaluationContext().apply { setVariable("input", input) }
            parser.parseExpression(expr).getValue(ctx)?.toString() ?: "null"
        } catch (_: Exception) {
            input?.toString() ?: "null"
        }
    }
}

/**
 * async 装饰器 — 异步执行，立即返回 asyncId；任务完成后通过 [AsyncCallback] 回调结果。
 */
class AsyncDecorator(
    private val executor: Executor,
    private val callback: AsyncCallback,
    private val taskInterceptor: TaskInterceptor = TaskInterceptor.NOOP
) : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "async:ioPool"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
        WorkflowFunction { input ->
            val asyncId = UUID.randomUUID().toString()
            val meta = input.meta
            val executionId = meta?.executionId ?: ""
            val workflowId = meta?.workflowId ?: node.workflowId
            val workflowName = meta?.workflowName ?: ""
            val appGroup = meta?.appGroup

            taskInterceptor.wrap(executor).execute {
                val context = { status: AsyncCallbackStatus, output: Any?, errorMsg: String? ->
                    AsyncCallbackContext(
                        asyncId = asyncId,
                        executionId = executionId,
                        workflowId = workflowId,
                        workflowName = workflowName,
                        appGroup = appGroup,
                        nodeId = node.id,
                        nodeName = node.name,
                        functionRef = node.functionRef,
                        status = status,
                        output = output,
                        errorMsg = errorMsg
                    )
                }
                try {
                    val result = function.apply(input)
                    callback.publish(context(AsyncCallbackStatus.SUCCESS, result.output, null))
                } catch (e: Exception) {
                    log.error("Async node [${node.name}] execution failed: ${e.message}", e)
                    callback.publish(context(AsyncCallbackStatus.FAILED, null, e.message))
                }
            }

            FunctionResult.success(
                mapOf(
                    "asyncId" to asyncId,
                    "status" to "PENDING",
                    "executionId" to executionId
                )
            )
        }
}

/**
 * logging 装饰器 — 函数执行前后打印入参和结果日志
 */
class LoggingDecorator : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "logging:default"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> =
        WorkflowFunction { input ->
            val start = System.currentTimeMillis()
            log.info { "[LOG] workflow=${node.workflowId} node=${node.name} function=${node.functionRef} inputs=${input.nodeParams}" }
            try {
                val result = function.apply(input)
                val duration = System.currentTimeMillis() - start
                log.info {
                    "[LOG] workflow=${node.workflowId} " +
                            "node=${node.name} function=${node.functionRef}," +
                            "success, duration=${duration}ms, output=${result.output}"
                }
                result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - start
                log.warn {
                    "[LOG] workflow=${node.workflowId} " +
                            "node=${node.name} function=${node.functionRef}," +
                            "failed, duration=${duration}ms, error=${e.message}"
                }
                throw e
            }
        }
}
