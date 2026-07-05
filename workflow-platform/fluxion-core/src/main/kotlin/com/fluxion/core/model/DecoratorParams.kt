package com.fluxion.core.model

/**
 * 获取指定装饰器的参数 Map。
 *
 * 用于替代连续多次调用 [WorkflowNode.getDecoratorParam] 时重复查找外层 decoratorParams 的开销。
 */
fun WorkflowNode.decoratorParams(decoratorName: String): Map<String, Any>? =
    decoratorParams?.get(decoratorName)

/**
 * 获取工作流级装饰器的参数 Map。
 *
 * 参数读取复用同一套 [intParam]/[longParam]/[stringParam]/[booleanParam] 扩展函数。
 */
fun WorkflowDefinition.decoratorParams(decoratorName: String): Map<String, Any>? =
    workflowDecoratorParams?.get(decoratorName)

/** 从装饰器参数 Map 中读取 Int，支持 Number 自动转换。 */
fun Map<String, Any>?.intParam(key: String, defaultValue: Int = 0): Int =
    when (val v = this?.get(key)) {
        is Number -> v.toInt()
        null -> defaultValue
        else -> throw IllegalArgumentException("Decorator param '$key' expected int, got ${v::class.simpleName}")
    }

/** 从装饰器参数 Map 中读取 Long，支持 Number 自动转换。 */
fun Map<String, Any>?.longParam(key: String, defaultValue: Long = 0L): Long =
    when (val v = this?.get(key)) {
        is Number -> v.toLong()
        null -> defaultValue
        else -> throw IllegalArgumentException("Decorator param '$key' expected long, got ${v::class.simpleName}")
    }

/** 从装饰器参数 Map 中读取 String。 */
fun Map<String, Any>?.stringParam(key: String, defaultValue: String? = null): String? =
    when (val v = this?.get(key)) {
        is String -> v
        null -> defaultValue
        else -> v.toString()
    }

/** 从装饰器参数 Map 中读取 Boolean。 */
fun Map<String, Any>?.booleanParam(key: String, defaultValue: Boolean = false): Boolean =
    when (val v = this?.get(key)) {
        is Boolean -> v
        null -> defaultValue
        else -> v.toString().toBooleanStrictOrNull() ?: defaultValue
    }
