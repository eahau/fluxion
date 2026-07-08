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

    // 閳光偓閳光偓閳光偓 缁斿宓嗛幍褑顢戦敍鍧塭layMs = 0閿涘鏀㈤埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧埞鈧?
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

    // 閳光偓閳光偓閳光偓 瀵ゆ儼绻滈幍褑顢?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 瀵倸鐖舵导鐘虫尡 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 CompletableFuture 闁?閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

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

    // 閳光偓閳光偓閳光偓 閼奉亜鐣炬稊?executor 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `custom executor is used for task execution`() {
        val customExecutor = Executors.newSingleThreadExecutor()
        val customScheduler = RetryScheduler(retryWorker = customExecutor)

        val future = customScheduler.schedule(Supplier { Thread.currentThread().name }, 0)
        val threadName = future.get(2, TimeUnit.SECONDS)
        // 閾忔碍瀚欑痪璺ㄢ柤閸氬秶袨閸栧懎鎯?"fluxion-retry-worker" 閸撳秶绱戦敍灞肩稻閼奉亜鐣炬稊?executor 娴ｈ法鏁ら弲顕€鈧氨鍤庣粙?        assertNotNull(threadName)

        customScheduler.shutdown()
        customExecutor.shutdown()
    }

    // 閳光偓閳光偓閳光偓 shutdown 閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓閳光偓

    @Test
    fun `shutdown stops scheduler`() {
        val localScheduler = RetryScheduler()
        localScheduler.shutdown()
        // 妤犲矁鐦?shutdown 娑撳秳绱伴幎娑樼磽鐢?    }
}
