package com.fluxion.admin.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug 接口安全守卫
 *
 * 三层防护：
 *   1. 全局并发容量限制（maxConcurrent）
 *   2. 单 IP 速率限制（maxPerMinutePerIp）
 *   3. 角色校验（需要 DEVELOPER 角色，可通过 Spring Security 扩展实现）
 *
 * 采用简单滑动计数器（内存），生产环境可替换为 Redis ZSET 实现。
 */
@Component
class DebugSecurityGuard(
    @Value("\${workflow.debug.max-concurrent:10}")          private val maxConcurrent: Int,
    @Value("\${workflow.debug.max-per-minute-per-ip:30}")   private val maxPerMinutePerIp: Int,
    @Value("\${workflow.debug.enabled:true}")               private val debugEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val activeCount = AtomicInteger(0)

    /** IP → (windowStartMs, count) */
    private val ipCounters = ConcurrentHashMap<String, IpCounter>()

    // ─── Public API ──────────────────────────────────────────────

    /**
     * 前置校验（在 Controller 方法入口调用）
     * @throws DebugNotEnabledException  debug 功能未开启
     * @throws DebugCapacityException    并发容量已满
     * @throws DebugRateLimitException   IP 速率超限
     */
    fun checkAndAcquire(request: HttpServletRequest) {
        if (!debugEnabled) {
            throw DebugNotEnabledException("Debug feature is disabled on this instance")
        }

        val ip = resolveIp(request)

        // 速率限制
        val counter = ipCounters.computeIfAbsent(ip) { IpCounter() }
        counter.increment(maxPerMinutePerIp, ip)

        // 并发容量
        val current = activeCount.incrementAndGet()
        if (current > maxConcurrent) {
            activeCount.decrementAndGet()
            log.warn { "Debug capacity full: current=${current - 1} max=$maxConcurrent" }
            throw DebugCapacityException("Debug capacity full ($maxConcurrent concurrent sessions), try again later")
        }
    }

    /** 执行结束后释放并发计数 */
    fun release() {
        activeCount.decrementAndGet()
    }

    // ─── IP 解析 ─────────────────────────────────────────────────

    private fun resolveIp(request: HttpServletRequest): String =
        (request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddr)
            .take(64)

    // ─── 内部计数器 ──────────────────────────────────────────────

    private class IpCounter {
        @Volatile private var windowStart = System.currentTimeMillis()
        private val count = AtomicInteger(0)

        fun increment(limit: Int, ip: String) {
            val now = System.currentTimeMillis()
            if (now - windowStart > 60_000L) {
                // 新窗口：重置
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

    // ─── 异常类 ──────────────────────────────────────────────────

    class DebugNotEnabledException(msg: String)  : RuntimeException(msg)
    class DebugCapacityException(msg: String)    : RuntimeException(msg)
    class DebugRateLimitException(msg: String)   : RuntimeException(msg)
}
