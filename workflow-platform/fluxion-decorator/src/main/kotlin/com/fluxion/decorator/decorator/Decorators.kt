@file:Suppress("unused")

package com.fluxion.decorator.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Node-level decorator SPI -- enhances [WorkflowFunction] with cross-cutting concerns.
 *
 * Implementations add behaviour such as metrics collection, distributed tracing,
 * rate limiting, caching, asynchronous execution and structured logging around
 * individual node function invocations.
 *
 * Decorators are looked up by `name()` in [DecoratorRegistry] and applied in the
 * order declared by [WorkflowNode.decorators].
 */
interface NodeDecorator {

    /**
     * Unique decorator identifier used in [WorkflowNode.decorators] and
     * as the key for per-node parameters in [WorkflowNode.decoratorParams].
     */
    fun name(): String

    /**
     * Wrap `function` with additional behaviour for the given `node`.
     *
     * The returned [WorkflowFunction] **must** eventually delegate to the
     * original function unless it short-circuits intentionally (e.g. rate
     * limit or cache hit). Implementations must be exception-safe: any
     * resources acquired before delegation must be released in a `finally`
     * block.
     *
     * @param function the inner node function to wrap
     * @param node     the workflow node being executed; access
     *                 `node.decoratorParams(name())` for typed configuration
     */
    fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any>
}

/**
 * Registry for [NodeDecorator] implementations -- thread-safe, backed by a
 * [ConcurrentHashMap].
 *
 * Used by [com.fluxion.core.engine.WorkflowEngine] when resolving the decorator
 * pipeline for a node. Spring auto-configuration collects all [NodeDecorator]
 * beans and calls [registerAll] at startup.
 */
class DecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, NodeDecorator>()

    /** Register a single decorator. Silently overwrites any previous registration. */
    fun register(decorator: NodeDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered decorator: ${decorator.name()}" }
    }

    /** Register a collection of decorators. Invokes [register] in iteration order. */
    fun registerAll(decoratorList: Collection<NodeDecorator>) {
        decoratorList.forEach(::register)
    }

    /**
     * Look up a decorator by name.
     *
     * @throws DecoratorNotFoundException if no decorator with `name` is registered
     */
    fun resolve(name: String): NodeDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    /** Return `true` if a decorator with `name` is currently registered. */
    fun contains(name: String): Boolean = decorators.containsKey(name)

    /** Return a snapshot list of currently registered decorator names. */
    fun listNames(): List<String> = decorators.keys().toList()
}

/** Terminal status of an asynchronously executed node callback. */
enum class AsyncCallbackStatus {
    /** The wrapped function completed normally (output is available). */
    SUCCESS,
    /** The wrapped function threw an exception (errorMsg is available). */
    FAILED
}

/**
 * Immutable context object delivered to an [AsyncCallback] subscriber when an
 * `async:ioPool`-decorated node finishes (successfully or with an error).
 *
 * Contains enough information for the admin-console / calling service to
 * correlate the result with the original workflow request via `executionId`,
 * `workflowId` and `asyncId`.
 */
data class AsyncCallbackContext(
    /** Opaque unique id returned from the decorated call (PENDING response). */
    val asyncId: String,
    /** Engine execution id (from [com.fluxion.core.value.ExecutionMeta]). */
    val executionId: String,
    /** Owning workflow id. */
    val workflowId: String,
    /** Human-readable workflow name. */
    val workflowName: String,
    /** Optional application group for multi-tenant deployments. */
    val appGroup: String?,
    /** Id of the node that executed asynchronously. */
    val nodeId: String,
    /** Human-readable node name. */
    val nodeName: String,
    /** Function reference that was invoked. */
    val functionRef: String,
    /** Final status of the asynchronous invocation. */
    val status: AsyncCallbackStatus,
    /** Function output on [AsyncCallbackStatus.SUCCESS]; `null` on failure. */
    val output: Any?,
    /** Exception message on [AsyncCallbackStatus.FAILED]; `null` on success. */
    val errorMsg: String?
)

/**
 * Asynchronous result delivery SPI used by `AsyncDecorator`.
 *
 * Implementations bridge out of the engine: workflow-admin provides Kafka /
 * HTTP based publishers that forward [AsyncCallbackContext] instances to the
 * caller or admin console.
 */
interface AsyncCallback {
    /**
     * Deliver the result of an asynchronously completed node.
     *
     * Implementations must never throw -- the engine does not swallow callback
     * exceptions and the failure would be surfaced to the worker thread pool.
     */
    fun publish(context: AsyncCallbackContext)
}

/**
 * Cache storage SPI used by the `cache:local` node decorator.
 *
 * Fluxion ships a built-in [com.github.benmanes.caffeine.cache.Caffeine]
 * backed implementation; deployments requiring a distributed cache should
 * register a Redis-based bean instead.
 */
interface CacheStore {
    /** Return the cached value for `key` or `null` if absent / expired. */
    fun get(key: String): Any?

    /**
     * Store `value` under `key` with a time-to-live.
     *
     * @param ttl  magnitude of the expiry duration
     * @param unit unit for `ttl`
     */
    fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit)

    /** Evict the entry under `key` immediately (no-op if absent). */
    fun evict(key: String)
}
