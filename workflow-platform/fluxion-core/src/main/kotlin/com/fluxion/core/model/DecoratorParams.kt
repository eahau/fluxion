package com.fluxion.core.model

/**
 * Helpers for extracting typed values from the loosely-typed decorator
 * parameter maps.
 *
 * Decorator parameters arrive from the admin console as JSON and are
 * stored as flat `Map<String, Any>` records.  These extensions let a
 * decorator pull a key with a sensible default and a clear error if
 * the raw JSON shape is incompatible with the declared type.
 */

/**
 * Look up a named decorator's parameter sub-map from a node.
 *
 * Returns `null` when the node has no decoratorParams map at all or
 * the decorator is not keyed in it.  Companion of
 * [WorkflowNode.getDecoratorParam] (which also supplies a default + cast).
 */
fun WorkflowNode.decoratorParams(decoratorName: String): Map<String, Any>? =
    decoratorParams?.get(decoratorName)

/**
 * Look up a named decorator's parameter sub-map from a workflow definition.
 *
 * Mirrors the node-level helper for workflow-scoped decorators.
 */
fun WorkflowDefinition.decoratorParams(decoratorName: String): Map<String, Any>? =
    workflowDecoratorParams?.get(decoratorName)

/** Extract an Int param, returning [defaultValue] on null or type-mismatch-safe fallback. */
fun Map<String, Any>?.intParam(key: String, defaultValue: Int = 0): Int =
    when (val v = this?.get(key)) {
        is Number -> v.toInt()
        null -> defaultValue
        else -> throw IllegalArgumentException("Decorator param '$key' expected int, got ${v::class.simpleName}")
    }

/** Extract a Long param; numeric coercion matches [intParam]. */
fun Map<String, Any>?.longParam(key: String, defaultValue: Long = 0L): Long =
    when (val v = this?.get(key)) {
        is Number -> v.toLong()
        null -> defaultValue
        else -> throw IllegalArgumentException("Decorator param '$key' expected long, got ${v::class.simpleName}")
    }

/** Extract a String param; any non-null value is coerced via `toString()`. */
fun Map<String, Any>?.stringParam(key: String, defaultValue: String? = null): String? =
    when (val v = this?.get(key)) {
        is String -> v
        null -> defaultValue
        else -> v.toString()
    }

/** Extract a Boolean param; strict parse via `toBooleanStrictOrNull` with fallback. */
fun Map<String, Any>?.booleanParam(key: String, defaultValue: Boolean = false): Boolean =
    when (val v = this?.get(key)) {
        is Boolean -> v
        null -> defaultValue
        else -> v.toString().toBooleanStrictOrNull() ?: defaultValue
    }
