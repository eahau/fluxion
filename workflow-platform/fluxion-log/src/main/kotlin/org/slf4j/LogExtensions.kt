package org.slf4j

/**
 * Lazy SLF4J logging extensions for Kotlin.
 *
 * Provides inline extension functions on [Logger] that accept a lambda producing the
 * log message. The message is only materialised if the corresponding log level is
 * enabled, avoiding expensive string construction when logging is disabled.
 *
 * Usage:
 * ```
 * private val log = LoggerFactory.getLogger(javaClass)
 * log.debug { "Processing request id=${request.id} user=${user.name}" }
 * log.error(ex) { "Failed to persist node [$nodeId]" }
 * ```
 */

inline fun Logger.debug(message: () -> String) {
    if (isDebugEnabled) debug(message())
}

inline fun Logger.info(message: () -> String) {
    if (isInfoEnabled) info(message())
}

inline fun Logger.warn(message: () -> String) {
    if (isWarnEnabled) warn(message())
}

inline fun Logger.error(message: () -> String) {
    if (isErrorEnabled) error(message())
}

inline fun Logger.trace(message: () -> String) {
    if (isTraceEnabled) trace(message())
}

inline fun Logger.debug(throwable: Throwable, message: () -> String) {
    if (isDebugEnabled) debug(message(), throwable)
}

inline fun Logger.info(throwable: Throwable, message: () -> String) {
    if (isInfoEnabled) info(message(), throwable)
}

inline fun Logger.warn(throwable: Throwable, message: () -> String) {
    if (isWarnEnabled) warn(message(), throwable)
}

inline fun Logger.error(throwable: Throwable, message: () -> String) {
    if (isErrorEnabled) error(message(), throwable)
}

inline fun Logger.trace(throwable: Throwable, message: () -> String) {
    if (isTraceEnabled) trace(message(), throwable)
}
