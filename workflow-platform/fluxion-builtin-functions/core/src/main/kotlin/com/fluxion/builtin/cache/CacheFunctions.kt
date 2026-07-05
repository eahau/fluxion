package com.fluxion.builtin.cache

import com.fluxion.builtin.BuiltinFunction
import com.fluxion.builtin.meta.BuiltinFunctionMetas
import com.fluxion.core.decorator.CacheStore
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.redis.RedisKey
import com.fluxion.core.value.FunctionResult
import org.slf4j.LoggerFactory
import org.slf4j.debug
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private val KEY_PATTERN = Pattern.compile("\\{([^}]+)}")


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
 * 内置缓存读取函数（builtin:cacheGet）
 */
class CacheGetFunction(private val cacheStore: CacheStore) : WorkflowFunction<Any?>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun meta() = BuiltinFunctionMetas.CACHE_GET

    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val keyTemplate = input.requireParam<String>("key")

        val resolvedKey = resolveKey(keyTemplate, input)
        log.debug { "CacheGetFunction: key=$resolvedKey" }

        return FunctionResult.success(cacheStore.get(resolvedKey))
    }
}

/**
 * 内置缓存写入函数（builtin:cacheSet）
 */
class CacheSetFunction(private val cacheStore: CacheStore) : WorkflowFunction<Boolean>, BuiltinFunction {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun meta() = BuiltinFunctionMetas.CACHE_SET

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
