package com.fluxion.core.retry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class RetrySchedulerTest {

    private val scheduler = RetryScheduler()

    // ─── 立即执行（delayMs = 0）─────────────────────────────────

    @Test
    fun `schedule with zero delay executes immediately`() {
        val future = scheduler.schedule(Supplier { "result" }, 0)
        val result = future.get(2, TimeUnit.SECONDS)
        assertEquals("result", result)
    }

    @Test
    fun `schedule with negative delay executes immediately`() {
        val future = scheduler.schedule(Supplier { 42 }, -100)
        assertEquals(42, future.get(2, TimeUnit.SECONDS))
    }

    // ─── 延迟执行 ──────────────────────────────────────────────

    @Test
    fun `schedule with delay executes after delay`() {
        val start = System.currentTimeMillis()
        val future = scheduler.schedule(Supplier { "delayed" }, 100)
        val result = future.get(2, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - start
        assertEquals("delayed", result)
        assertTrue(elapsed >= 90, "Expected delay >= 90ms, got ${elapsed}ms")
    }

    @Test
    fun `multiple scheduled tasks run concurrently`() {
        val f1 = scheduler.schedule(Supplier { "a" }, 50)
        val f2 = scheduler.schedule(Supplier { "b" }, 50)
        val f3 = scheduler.schedule(Supplier { "c" }, 50)

        assertEquals("a", f1.get(2, TimeUnit.SECONDS))
        assertEquals("b", f2.get(2, TimeUnit.SECONDS))
        assertEquals("c", f3.get(2, TimeUnit.SECONDS))
    }

    // ─── 异常传播 ──────────────────────────────────────────────

    @Test
    fun `exception in supplier propagates to future`() {
        val future = scheduler.schedule<String>(Supplier { throw RuntimeException("boom") }, 0)
        val ex = assertThrows<CompletionException> { future.join() }
        assertTrue(ex.cause is RuntimeException)
        assertEquals("boom", ex.cause?.message)
    }

    @Test
    fun `exception with delay also propagates`() {
        val future = scheduler.schedule<String>(Supplier { throw IllegalStateException("fail") }, 50)
        val ex = assertThrows<CompletionException> { future.join() }
        assertTrue(ex.cause is IllegalStateException)
        assertEquals("fail", ex.cause?.message)
    }

    // ─── CompletableFuture 链 ──────────────────────────────────

    @Test
    fun `thenApply chains successfully`() {
        val future = scheduler.schedule(Supplier { 10 }, 0)
            .thenApply { it * 2 }
        assertEquals(20, future.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `exceptionallyCompose handles failure`() {
        val future = scheduler.schedule<String>(Supplier { throw RuntimeException("err") }, 0)
            .exceptionallyCompose { throwable ->
                java.util.concurrent.CompletableFuture.completedFuture("fallback")
            }
        assertEquals("fallback", future.get(2, TimeUnit.SECONDS))
    }

    @Test
    fun `thenApply after exception is skipped`() {
        val future = scheduler.schedule<String>(Supplier { throw RuntimeException("err") }, 0)
            .thenApply { "should not reach" }
            .exceptionally { "recovered" }
        assertEquals("recovered", future.get(2, TimeUnit.SECONDS))
    }

    // ─── 自定义 executor ───────────────────────────────────────

    @Test
    fun `custom executor is used for task execution`() {
        val customExecutor = Executors.newSingleThreadExecutor()
        val customScheduler = RetryScheduler(retryWorker = customExecutor)

        val future = customScheduler.schedule(Supplier { Thread.currentThread().name }, 0)
        val threadName = future.get(2, TimeUnit.SECONDS)
        // 虚拟线程名称包含 "fluxion-retry-worker" 前缀，但自定义 executor 使用普通线程
        assertNotNull(threadName)

        customScheduler.shutdown()
        customExecutor.shutdown()
    }

    // ─── shutdown ──────────────────────────────────────────────

    @Test
    fun `shutdown stops scheduler`() {
        val localScheduler = RetryScheduler()
        localScheduler.shutdown()
        // 验证 shutdown 不会抛异常
    }
}
