package com.fluxion.admin.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.*
import org.slf4j.warn
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Three-layered gatekeeper for debug / step-through endpoints.
 *
 * 1. **Global concurrency cap** (`maxConcurrent`) – avoids saturating the admin instance with
 *    heavy in-memory workflow executions.
 * 2. **Per-IP sliding rate limit** (`maxPerMinutePerIp`) – based on a 60-second window with
 *    per-IP counters held in a `ConcurrentHashMap`.
 * 3. **Role check** (DEVELOPER role) – enforced upstream by Spring Security; this class does
 *    not re-implement it.
 *
 * The in-memory counters are intentionally simple. For clustered production deployments the
 * implementation should be swapped for a Redis-backed ZSET / token-bucket approach.
 */
@Component
class DebugSecurityGuard(
    @Value("\${workflow.debug.max-concurrent:10}")          private val maxConcurrent: Int,
    @Value("\${workflow.debug.max-per-minute-per-ip:30}")   private val maxPerMinutePerIp: Int,
    @Value("\${workflow.debug.enabled:true}")               private val debugEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeCount = AtomicInteger(0)

    /** IP → (windowStartMs, count) sliding-window counter. */
    private val ipCounters = ConcurrentHashMap<String, IpCounter>()

    // === Public API =============================================================================

    /**
     * Pre-flight check invoked at the very top of each debug controller handler.
     *
     * @throws DebugNotEnabledException if the debug feature toggle is off
     * @throws DebugCapacityException   if the global concurrency pool is exhausted
     * @throws DebugRateLimitException  if the calling IP exceeded its per-minute allowance
     */
    fun checkAndAcquire(request: HttpServletRequest) {
        if (!debugEnabled) {
            throw DebugNotEnabledException("Debug feature is disabled on this instance")
        }

        val ip = resolveIp(request)

        val counter = ipCounters.computeIfAbsent(ip) { IpCounter() }
        counter.increment(maxPerMinutePerIp, ip)

        val current = activeCount.incrementAndGet()
        if (current > maxConcurrent) {
            activeCount.decrementAndGet()
            log.warn { "Debug capacity full: current=${current - 1} max=$maxConcurrent" }
            throw DebugCapacityException("Debug capacity full ($maxConcurrent concurrent sessions), try again later")
        }
    }

    /** Release a concurrency permit after a debug session completes (or fails). */
    fun release() {
        activeCount.decrementAndGet()
    }

    // === IP resolution ==========================================================================

    private fun resolveIp(request: HttpServletRequest): String =
        (request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddr)
            .take(64)

    // === Per-IP counter =========================================================================

    private class IpCounter {
        @Volatile private var windowStart = System.currentTimeMillis()
        private val count = AtomicInteger(0)

        fun increment(limit: Int, ip: String) {
            val now = System.currentTimeMillis()
            if (now - windowStart > 60_000L) {
                windowStart = now
                count.set(0)
            }
            val c = count.incrementAndGet()
            if (c > limit) {
                count.decrementAndGet()
                throw DebugRateLimitException("Debug rate limit exceeded for IP: $ip ($limit req/min)")
            }
        }
    }

    // === Exception types ========================================================================

    class DebugNotEnabledException(msg: String)  : RuntimeException(msg)
    class DebugCapacityException(msg: String)    : RuntimeException(msg)
    class DebugRateLimitException(msg: String)   : RuntimeException(msg)
}
