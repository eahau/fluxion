package com.fluxion.decorator.lock

import com.fluxion.core.lock.DistributedLockProvider
import com.fluxion.core.lock.LockParams
import com.fluxion.core.lock.lockSuspending
import com.fluxion.core.model.booleanParam
import com.fluxion.core.model.intParam
import com.fluxion.core.model.longParam
import com.fluxion.core.model.stringParam
import com.fluxion.core.redis.RedisKey
import org.slf4j.Logger
import org.slf4j.debug
import org.slf4j.warn

/**
 * 瑁呴グ鍣ㄦ墽琛屼笂涓嬫枃銆? *
 * 灏嗚妭鐐圭骇/宸ヤ綔娴佺骇瑁呴グ鍣ㄤ腑閲嶅鐨勫弬鏁般€侀攣閿瀯閫犱俊鎭€丼pEL 鍙橀噺缁熶竴鎵撳寘锛? * 浣挎牳蹇冨姞閿侀€昏緫鏃犻渶鍏冲績璋冪敤鏂规槸 [com.fluxion.core.function.WorkflowFunction] 杩樻槸 suspend 鍑芥暟銆? */
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
 * 鍩轰簬 [LockContext] 鐨勯樆濉炲姞閿佹墽琛屻€? *
 * 鍐呴儴瀹屾垚鍙傛暟瑙ｆ瀽銆侀攣閿瀯閫犮€?{path} 妯℃澘姹傚€硷紝鐒跺悗濮旀墭缁?[DistributedLockProvider.lock]銆? */
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
 * 鍩轰簬 [LockContext] 鐨?suspend 鍔犻攣鎵ц銆? *
 * 閿佺殑鑾峰彇涓庨噴鏀惧湪 [Dispatchers.IO] 涓墽琛岋紱[action] 鍦ㄨ皟鐢ㄦ柟鍗忕▼璋冨害鍣ㄤ腑鎵ц銆? */
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

/** 瀵归攣閿ā鏉夸腑鐨?${path} 鍗犱綅绗︽眰鍊硷紝骞舵寜瑙勮寖鍖呰涓洪泦缇?hash tag 鏍煎紡銆?*/
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

/** 灏?${path} 褰㈠紡鐨勫崰浣嶇鏇挎崲涓哄疄闄呭€硷紙path 鏀寔鐐瑰彿宓屽璺緞锛夛紱鏃犲崰浣嶇鏃跺師鏍疯繑鍥炪€?*/
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
 * 鎸夌偣鍙疯矾寰勪粠 Map 涓В鏋愬祵濂楀€笺€? * 渚嬪 path="user.address.city" 浠?{user: {address: {city: "Beijing"}}} 涓彇鍑?"Beijing"銆? */
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

/** 鏋勯€犲熀纭€閿侀敭锛岀粨鏋滅鍚?{appGroup:domain}:businessKey 瑙勮寖銆?*/
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

/** 瑙ｆ瀽閿佽楗板櫒鍙傛暟銆?*/
private fun resolveLockParams(params: Map<String, Any>?): LockParams = LockParams(
    waitMillis = params.longParam("waitMillis", 0L),
    leaseMillis = params.longParam("leaseMillis", 30_000L),
    retry = params.intParam("retry", 0),
    retryIntervalMillis = params.longParam("retryIntervalMillis", 100L),
    sync = params.booleanParam("sync", false),
    failOnLocked = params.booleanParam("failOnLocked", true)
)
