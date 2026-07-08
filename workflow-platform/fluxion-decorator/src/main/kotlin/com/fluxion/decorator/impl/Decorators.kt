package com.fluxion.decorator.impl

import com.fluxion.decorator.decorator.AsyncCallback
import com.fluxion.decorator.decorator.AsyncCallbackContext
import com.fluxion.decorator.decorator.AsyncCallbackStatus
import com.fluxion.decorator.decorator.CacheStore
import com.fluxion.decorator.decorator.NodeDecorator
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.*
import com.fluxion.decorator.ratelimit.RateLimitConfig
import com.fluxion.decorator.ratelimit.RateLimitStore
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
 * Node decorator that records Micrometer counters + timers for every
 * invocation.
 *
 * Counters emitted:
 *  - `workflow.node.success` (tagged: workflow, node)
 *  - `workflow.node.error`   (tagged: workflow, node, error=simple class name)
 *
 * Timer emitted:
 *  - `workflow.node.duration` (milliseconds)
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
 * Sliding-window rate limit decorator for individual nodes.
 *
 * Underlying permit storage is pluggable via [RateLimitStore] SPI:
 *  - Default: in-process [LocalRateLimitStore] (single JVM only).
 *  - Production: inject a Redis-backed [RateLimitStore] bean for multi-instance
 *    coordination.
 *
 * ### Configuration via `WorkflowNode.decoratorParams["ratelimit:slidingWindow"]`
 *
 * | Key              | Description                                       | Default           |
 * |------------------|---------------------------------------------------|-------------------|
 * | `upLimited`      | Max permits inside the sliding window             | 6                 |
 * | `cdSeconds`      | Cooldown window length (seconds)                  | 10                |
 * | `recoveryPerCd`  | Permits restored every `cdSeconds`                | equals `upLimited`|
 * | `rateLimitKey`   | Explicit lock key (highest priority)              | `ratelimit:<nodeId>` |
 * | `keyPrefix`      | Prepended to the generated business key           | `fluxion:ratelimit:` |
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
 * L1 (single-node) result cache decorator.
 *
 * Cache key resolution supports a subset of SpEL expressions evaluated against
 * the node's `directInput` via `#input` (the default). Results are stored under
 * a scoped [RedisKey] with a caller-supplied TTL.
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
 * Fire-and-forget async decorator: submits the real function to `executor`,
 * returns immediately with a PENDING envelope, then publishes the final
 * success / failure outcome through [AsyncCallback].
 *
 * Typical consumer is a long-running node (external HTTP call, DB batch
 * operation) where the HTTP caller does not want to block. The companion
 * result is forwarded out-of-band (Kafka / HTTP webhook) via the callback.
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
                    log.error(e) { "Async node [${node.name}] execution failed: ${e.message}" }
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
 * Structured logging decorator -- emits a before + after INFO line per node
 * invocation including inputs, success/failure status and wall-clock duration.
 *
 * This is intentionally minimal; production deployments typically replace it
 * with a structured (JSON) logger + tracing integration.
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
