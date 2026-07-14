package com.fluxion.config.core

/**
 * Unified config parsing + validation result type — triple-state sealed class.
 *
 * Used in [AbstractKeyedConfigSubscriber]'s resolve pipeline to express three outcomes:
 * - [Success]: parse + validation passed, [snapshot] can be used directly
 * - [ParseFailed]: JSON/schema parse error, [error] carries exception details
 * - [ValidationFailed]: parse succeeded but business validation failed, [reason] carries rejection rationale
 *
 * Callers decide downstream action based on result: use, degrade to previous good value, or skip.
 */
sealed class ConfigResolveResult<out T> {

    /** Parse + validation passed */
    data class Success<T>(val snapshot: T) : ConfigResolveResult<T>()

    /** JSON / schema parse error */
    data class ParseFailed(val error: Exception) : ConfigResolveResult<Nothing>()

    /** Parse succeeded but business validation failed */
    data class ValidationFailed(val reason: String) : ConfigResolveResult<Nothing>()
}
