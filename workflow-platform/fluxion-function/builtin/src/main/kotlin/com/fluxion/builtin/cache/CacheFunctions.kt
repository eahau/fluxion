package com.fluxion.builtin.cache

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.decorator.decorator.CacheStore
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.redis.RedisKey
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Pattern for `{paramName}` style placeholder substitution inside cache-key templates.
 */
private val KEY_PATTERN = Pattern.compile("\\{([^}]+)}")

/**
 * Resolves a cache-key template like `"user:{id}"` against the current node's
 * params + directInput map, and then wraps it with the app/workflow-scoped
 * prefix via [RedisKey.wrapIfNeeded] so different workflows/apps don't collide
 * on the same physical key.
 */
private fun resolveKey(template: String, input: NodeInput): String {
    val m = KEY_PATTERN.matcher(template)
    val sb = StringBuilder()
    while (m.find()) {
        val paramName = m.group(1)
        val value = input.nodeParams[paramName]
            ?: (input.directInput as? Map<*, *>)?.get(paramName)
        m.appendReplacement(sb, value?.toString() ?: paramName)
    }
    m.appendTail(sb)
    return RedisKey.wrapIfNeeded(
        input.meta?.appGroup,
        input.meta?.workflowId ?: "",
        sb.toString()
    )
}

/**
 * Built-in cache read function (`builtin:cacheGet`).
 *
 * Returns the cached value or `null` on miss. The actual storage is
 * abstracted via [CacheStore] so this works identically for Caffeine local
 * cache and remote Redis-backed stores.
 */
class CacheGetFunction(private val cacheStore: CacheStore) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:cacheGet"

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val keyTemplate = input.requireParam<String>("key")

        val resolvedKey = resolveKey(keyTemplate, input)
        log.debug { "CacheGetFunction: key=$resolvedKey" }

        return FunctionResult.success(cacheStore.get(resolvedKey))
    }
}

/**
 * Built-in cache write function (`builtin:cacheSet`).
 *
 * Stores directInput under the templated key with the requested TTL
 * (default 3600 seconds). Returns `true` unconditionally — the store
 * implementation swallows storage errors internally.
 */
class CacheSetFunction(private val cacheStore: CacheStore) : WorkflowFunction<Boolean>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override val functionName: String = "builtin:cacheSet"

    override fun apply(input: NodeInput): FunctionResult<Boolean> {
        val keyTemplate = input.requireParam<String>("key")

        val ttlSeconds = input.paramAsLong("ttlSeconds", 3600L)
        val resolvedKey = resolveKey(keyTemplate, input)
        val value = input.directInput

        log.debug { "CacheSetFunction: key=$resolvedKey, ttl=${ttlSeconds}s" }

        cacheStore.put(resolvedKey, value, ttlSeconds, TimeUnit.SECONDS)
        return FunctionResult.success(true)
    }
}
