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
 * metrics 瑁呴グ鍣?鈥?Micrometer 璁℃椂 & 璁℃暟
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
 * rateLimit 瑁呴グ鍣?鈥?婊戝姩绐楀彛闄愭祦銆?
 *
 * 閫氳繃 [RateLimitStore] SPI 瑙ｈ€﹀簳灞傚瓨鍌細
 * - 鏃犲閮ㄥ瓨鍌ㄦ椂锛屼娇鐢ㄥ唴缃殑 [LocalRateLimitStore]锛堝崟 JVM锛?
 * - 鍒嗗竷寮忓満鏅笅娉ㄥ叆 Redis 绛?[RateLimitStore] 瀹炵幇
 *
 * 閰嶇疆鍙傛暟锛堥€氳繃 [WorkflowNode.decoratorParams] 鐨?"ratelimit:slidingWindow" 閿級锛?
 * - `upLimited`:     绐楀彛鍐呮渶澶ц姹傛暟锛岄粯璁?6
 * - `cdSeconds`:     鎭㈠鍛ㄦ湡锛堢锛夛紝榛樿 10
 * - `recoveryPerCd`: 姣忎釜鍛ㄦ湡鎭㈠鐨勪护鐗屾暟锛岄粯璁ょ瓑浜?upLimited
 * - `rateLimitKey`:  鏄惧紡闄愭祦閿紝浼樺厛绾ф渶楂?
 * - `keyPrefix`:     閿墠缂€锛岄粯璁?"fluxion:ratelimit:"
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
 * cache 瑁呴グ鍣?鈥?L1 鏈湴缂撳瓨
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
 * async 瑁呴グ鍣?鈥?寮傛鎵ц锛岀珛鍗宠繑鍥?asyncId锛涗换鍔″畬鎴愬悗閫氳繃 [AsyncCallback] 鍥炶皟缁撴灉銆?
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
 * logging 瑁呴グ鍣?鈥?鍑芥暟鎵ц鍓嶅悗鎵撳嵃鍏ュ弬鍜岀粨鏋滄棩蹇?
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
