package com.fluxion.core.lock

import com.fluxion.core.engine.RuleEvaluator
import com.fluxion.core.model.booleanParam
import com.fluxion.core.model.intParam
import com.fluxion.core.model.longParam
import com.fluxion.core.model.stringParam
import com.fluxion.core.redis.RedisKey
import org.slf4j.Logger
import org.slf4j.debug
import org.slf4j.warn

/**
 * 锁装饰器执行上下文。
 *
 * 将节点级/工作流级装饰器中重复的参数、锁键构建信息、SpEL 变量统一打包，
 * 使核心加锁逻辑无需关心调用方是 [com.fluxion.core.function.WorkflowFunction] 还是 suspend 函数。
 */
data class LockContext(
    val params: Map<String, Any>?,
    val appGroup: String?,
    val domain: String,
    val defaultPrefix: String,
    val defaultBusinessKey: String,
    val contextDescription: String,
    val variables: Map<String, Any?>
)

/**
 * 基于 [LockContext] 的阻塞加锁执行。
 *
 * 内部完成参数解析、锁键构建、${'$'}{path} 模板求值，然后委托给 [DistributedLockProvider.lock]。
 */
fun <T> DistributedLockProvider.lock(
    context: LockContext,
    log: Logger,
    action: () -> T
): T {
    val lockParams = resolveLockParams(context.params)
    val resolvedKey = resolveLockKey(context, log)
    log.debug { "Executing with lock for ${context.contextDescription} key=$resolvedKey" }
    return lock(lockKey = resolvedKey, params = lockParams, action = action)
}

/**
 * 基于 [LockContext] 的 suspend 加锁执行。
 *
 * 锁的获取与释放在 [Dispatchers.IO] 中执行；[action] 在调用方协程调度器中执行。
 */
suspend fun <T> DistributedLockProvider.lockSuspending(
    context: LockContext,
    log: Logger,
    action: suspend () -> T
): T {
    val lockParams = resolveLockParams(context.params)
    val resolvedKey = resolveLockKey(context, log)
    log.debug { "Executing with lock for ${context.contextDescription} key=$resolvedKey" }
    return lockSuspending(lockKey = resolvedKey, params = lockParams, action = action)
}

/** 对锁键模板中的 `${'$'}{path}` 占位符求值，并按规范包装为集群 hash tag 格式。 */
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

private const val PLACEHOLDER_START = "${'$'}{"

/** 将 `${'$'}{path}` 形式的占位符替换为实际值（path 支持点号嵌套路径）；无占位符时原样返回。 */
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
        val value = RuleEvaluator.resolveFieldValue(path, variables)?.toString()
        if (value == null) {
            log.warn { "Lock key template path [$path] resolved to null, using empty" }
        }
        sb.append(value ?: "")
        i = end + 1
    }
    return sb.toString()
}

/** 构造基础锁键，结果符合 {appGroup:domain}:businessKey 规范。 */
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

/** 解析锁装饰器参数。 */
private fun resolveLockParams(params: Map<String, Any>?): LockParams = LockParams(
    waitMillis = params.longParam("waitMillis", 0L),
    leaseMillis = params.longParam("leaseMillis", 30_000L),
    retry = params.intParam("retry", 0),
    retryIntervalMillis = params.longParam("retryIntervalMillis", 100L),
    sync = params.booleanParam("sync", false),
    failOnLocked = params.booleanParam("failOnLocked", true)
)


