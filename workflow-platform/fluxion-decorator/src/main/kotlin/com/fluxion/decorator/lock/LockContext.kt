package com.fluxion.decorator.lock

import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.lock.LockParams
import com.fluxion.core.lock.lockSuspending
import com.fluxion.core.model.booleanParam
import com.fluxion.core.model.intParam
import com.fluxion.core.model.longParam
import com.fluxion.core.model.stringParam
import com.fluxion.redis.RedisKey
import org.slf4j.Logger
import org.slf4j.debug
import org.slf4j.warn

/**
 * Aggregated parameters required by both node-level and workflow-level lock
 * decorators.
 *
 * Shared so the template resolution, key construction and parameter parsing
 * logic lives in one place instead of being duplicated between
 * [DistributedLockDecorator] and [WorkflowLockDecorator].
 */
data class LockContext(
    /** Raw per-decorator params map (from `decoratorParams(...)`). */
    val params: Map<String, Any>?,
    /** Application group (used for hash-tag scoping of Redis keys). */
    val appGroup: String?,
    /** Workflow / domain id embedded in the Redis key prefix. */
    val domain: String,
    /** Default prefix when params do not specify `prefix`. */
    val defaultPrefix: String,
    /** Default business key when params specify neither `lockKey` nor expression. */
    val defaultBusinessKey: String,
    /** Human-readable description used only for log messages. */
    val contextDescription: String,
    /** Variables made available to `${path}` template expressions. */
    val variables: Map<String, Any?>
)

/**
 * Blocking (non-suspend) entry point: parse params, resolve template key,
 * acquire + release around `action` via [DistributedLockProvider.lock].
 */
fun <T> DistributedLockProvider.lockWithContext(
    context: LockContext,
    log: Logger,
    action: () -> T
): T {
    val lockParams = resolveLockParams(context.params)
    val resolvedKey = resolveLockKey(context, log)
    log.debug { "Executing with lock for ${context.contextDescription} key=$resolvedKey" }
    return lock(resolvedKey, lockParams, action)
}

/**
 * Suspend entry point: same flow as [lockWithContext] but the lock acquire /
 * release steps are offloaded to `Dispatchers.IO` internally so the calling
 * coroutine never blocks.
 */
suspend fun <T> DistributedLockProvider.lockSuspendingWithContext(
    context: LockContext,
    log: Logger,
    action: suspend () -> T
): T {
    val lockParams = resolveLockParams(context.params)
    val resolvedKey = resolveLockKey(context, log)
    log.debug { "Executing with lock for ${context.contextDescription} key=$resolvedKey" }
    return lockSuspending(resolvedKey, lockParams, action)
}

/**
 * Template resolver for `${path}` style placeholders inside lock keys.
 * Wraps the resulting business key with the Redis hash-tag convention.
 */
private fun resolveLockKey(context: LockContext, log: Logger): String {
    val baseKey = buildBaseLockKey(
        params = context.params,
        appGroup = context.appGroup,
        domain = context.domain,
        defaultPrefix = context.defaultPrefix,
        defaultBusinessKey = context.defaultBusinessKey
    )
    val resolved = resolveTemplate(baseKey, context.variables, log)
    return RedisKey.wrapIfNeeded(context.appGroup, context.domain, resolved)
}

private const val PLACEHOLDER_START = "\${"

/**
 * Replace every `${path}` occurrence in `template` with the dot-path lookup of
 * `path` inside `variables`. Unknown paths produce an empty string and a
 * warning log (not a hard failure, because operators often add optional keys
 * incrementally).
 */
private fun resolveTemplate(template: String, variables: Map<String, Any?>, log: Logger): String {
    if (!template.contains(PLACEHOLDER_START)) return template
    val sb = StringBuilder()
    var i = 0
    while (i < template.length) {
        val start = template.indexOf(PLACEHOLDER_START, i)
        if (start == -1) {
            sb.append(template.substring(i))
            break
        }
        sb.append(template, i, start)
        val end = template.indexOf('}', start + PLACEHOLDER_START.length)
        if (end == -1) {
            sb.append(template, start, template.length)
            break
        }
        val path = template.substring(start + PLACEHOLDER_START.length, end).trim()
        val value = resolveFieldValue(path, variables)?.toString()
        if (value == null) {
            log.warn { "Lock key template path [$path] resolved to null, using empty" }
        }
        sb.append(value ?: "")
        i = end + 1
    }
    return sb.toString()
}

/**
 * Walk a dot-separated `path` through nested [Map]s. Non-map leafs match the
 * special `output` path only (matching the convention used in node inputs).
 */
private fun resolveFieldValue(field: String, nodeOutput: Any?): Any? {
    if (field.isBlank()) return nodeOutput
    if (nodeOutput !is Map<*, *>) return if (field == "output") nodeOutput else null
    val parts = field.split('.')
    var current: Any? = nodeOutput
    for (part in parts) {
        current = when (current) {
            is Map<*, *> -> current[part]
            else -> return null
        }
    }
    return current
}

/**
 * Build the non-templated base key. Priority exactly matches the admin console
 * docs: explicit `lockKey` > `lockKeyExpression` template > generated prefix+id.
 */
private fun buildBaseLockKey(
    params: Map<String, Any>?,
    appGroup: String?,
    domain: String,
    defaultPrefix: String,
    defaultBusinessKey: String
): String {
    params.stringParam("lockKey")?.takeIf { it.isNotBlank() }?.let {
        return RedisKey.wrapIfNeeded(appGroup, domain, it)
    }
    params.stringParam("lockKeyExpression")?.takeIf { it.isNotBlank() }?.let { return it }

    val prefix = params.stringParam("prefix") ?: defaultPrefix
    return RedisKey.format(appGroup, domain, "$prefix$defaultBusinessKey")
}

/** Parse decorator params into a typed [LockParams], with documented defaults. */
private fun resolveLockParams(params: Map<String, Any>?): LockParams = LockParams(
    waitMillis = params.longParam("waitMillis", 0L),
    leaseMillis = params.longParam("leaseMillis", 30_000L),
    retry = params.intParam("retry", 0),
    retryIntervalMillis = params.longParam("retryIntervalMillis", 100L),
    sync = params.booleanParam("sync", false),
    failOnLocked = params.booleanParam("failOnLocked", true)
)
