package com.fluxion.config.core

/**
 * 配置解析 + 校验的统一结果类型 — 三元密封类
 *
 * 用于在 [AbstractKeyedConfigSubscriber] 的 resolve 管线中显式表达：
 * - [Success]：解析 + 校验均通过，[snapshot] 可直接使用
 * - [ParseFailed]：JSON/格式解析失败，[error] 携带异常信息
 * - [ValidationFailed]：解析成功但业务校验未通过，[reason] 携带拒绝原因
 *
 * 调用方根据结果决定后续策略：使用、降级到上一个好值、或跳过。
 */
sealed class ConfigResolveResult<out T> {

    /** 解析 + 校验均通过 */
    data class Success<T>(val snapshot: T) : ConfigResolveResult<T>()

    /** JSON / 格式解析失败 */
    data class ParseFailed(val error: Exception) : ConfigResolveResult<Nothing>()

    /** 解析成功但业务校验未通过 */
    data class ValidationFailed(val reason: String) : ConfigResolveResult<Nothing>()
}
