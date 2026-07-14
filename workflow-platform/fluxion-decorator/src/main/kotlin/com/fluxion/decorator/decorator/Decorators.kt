@file:Suppress("unused")

package com.fluxion.decorator.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.*
import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.core.value.EngineResult
import com.fluxion.core.value.FunctionResult
import com.fluxion.decorator.engine.TaskInterceptor
import com.fluxion.decorator.ratelimit.LocalRateLimitStore
import com.fluxion.redis.RedisKey
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.slf4j.*
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface NodeDecorator {

    fun name(): String

    fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any>
}

class DecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, NodeDecorator>()

    fun register(decorator: NodeDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<NodeDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): NodeDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}

enum class AsyncCallbackStatus {
    SUCCESS,
    FAILED
}

data class AsyncCallbackContext(
    val asyncId: String,
    val executionId: String,
    val workflowId: String,
    val workflowName: String,
    val appGroup: String?,
    val nodeId: String,
    val nodeName: String,
    val functionRef: String,
    val status: AsyncCallbackStatus,
    val output: Any?,
    val errorMsg: String?
)

interface AsyncCallback {
    fun publish(context: AsyncCallbackContext)
}

interface CacheStore {
    fun get(key: String): Any?

    fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit)

    fun evict(key: String)
}

interface WorkflowDecorator {

    fun name(): String

    suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult
}

class WorkflowDecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, WorkflowDecorator>()

    fun register(decorator: WorkflowDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered workflow decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<WorkflowDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): WorkflowDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}

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

class RateLimitDecorator(private val store: RateLimitStore = LocalRateLimitStore()) : NodeDecorator {

    override fun name(): String = "ratelimit:slidingWindow"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        val config = resolveConfig(node)
        val key = resolveKey(node)

        return WorkflowFunction { input ->
            if (!store.tryAcquire(key, config)) {
                throw com.fluxion.core.exception.RateLimitExceededException(
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