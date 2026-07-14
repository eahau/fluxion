package com.fluxion.runtime.core.router

import com.fluxion.inbound.spi.UnifiedRequest
import com.fluxion.inbound.spi.UnifiedResponse
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.core.exception.LockAcquisitionException
import com.fluxion.test.lock.InMemoryDistributedLockProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavioural tests for [DistributedLockRouter].
 *
 * Uses [InMemoryDistributedLockProvider] (from the test-support module) as a
 * fast, deterministic lock backend that can simulate transient failures and
 * expose acquire/release state for assertions.
 */
class DistributedLockRouterTest {

    private lateinit var lockProvider: InMemoryDistributedLockProvider

    @BeforeEach
    fun setUp() {
        lockProvider = InMemoryDistributedLockProvider()
    }

    @Test
    fun `should acquire and release lock around workflow execution`() {
        val delegate = CountingRouter()
        val router = DistributedLockRouter(delegate, lockProvider)
        val request = UnifiedRequest(
            protocol = "HTTP:POST:/test",
            workflowId = "wf1",
            headers = mapOf(DistributedLockRouter.HEADER_LOCK_KEY to "wf:lock"),
            params = emptyMap(),
            rawBody = null
        )

        val response = runBlocking { router.executeSuspend(request) }

        assertTrue(response.success)
        assertEquals("ok", response.data)
        assertEquals(1, delegate.maxConcurrent.get())
        assertTrue(lockProvider.isReleased("wf:lock"))
    }

    @Test
    fun `should throw LockAcquisitionException when workflow lock is held`() {
        lockProvider.acquire("wf:lock", 5000L, 0L)

        val delegate = CountingRouter()
        val router = DistributedLockRouter(delegate, lockProvider)
        val request = UnifiedRequest(
            protocol = "HTTP:POST:/test",
            workflowId = "wf1",
            headers = mapOf(DistributedLockRouter.HEADER_LOCK_KEY to "wf:lock"),
            params = emptyMap(),
            rawBody = null
        )

        assertThrows(LockAcquisitionException::class.java) {
            runBlocking { router.executeSuspend(request) }
        }
        assertEquals(0, delegate.callCount.get())
    }

    @Test
    fun `should use param lock key when header absent`() {
        val delegate = CountingRouter()
        val router = DistributedLockRouter(delegate, lockProvider)
        val request = UnifiedRequest(
            protocol = "HTTP:POST:/test",
            workflowId = "wf1",
            headers = emptyMap(),
            params = mapOf(DistributedLockRouter.PARAM_LOCK_KEY to "wf:param-lock"),
            rawBody = null
        )

        runBlocking { router.executeSuspend(request) }
        assertTrue(lockProvider.isReleased("wf:param-lock"))
    }

    @Test
    fun `should retry acquire before failing on transient lock contention`() {
        lockProvider.transientFailures["wf:lock"] = AtomicInteger(2)

        val delegate = CountingRouter()
        val router = DistributedLockRouter(
            delegate,
            lockProvider,
            retry = 3,
            retryIntervalMillis = 10L
        )
        val request = UnifiedRequest(
            protocol = "HTTP:POST:/test",
            workflowId = "wf1",
            headers = mapOf(DistributedLockRouter.HEADER_LOCK_KEY to "wf:lock"),
            params = emptyMap(),
            rawBody = null
        )

        val response = runBlocking { router.executeSuspend(request) }

        assertTrue(response.success)
        assertEquals(3, lockProvider.acquireAttempts["wf:lock"]?.get())
    }

    @Test
    fun `should serialize concurrent workflow executions`() {
        val delegate = CountingRouter(sleepMillis = 10)
        val router = DistributedLockRouter(delegate, lockProvider)
        val request = UnifiedRequest(
            protocol = "HTTP:POST:/test",
            workflowId = "wf1",
            headers = mapOf(DistributedLockRouter.HEADER_LOCK_KEY to "wf:lock"),
            params = emptyMap(),
            rawBody = null
        )

        val threads = (1..5).map {
            Thread { runBlocking { router.executeSuspend(request) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, delegate.maxConcurrent.get())
    }

    /**
     * Test stub [InboundRouter] that counts invocations and tracks the
     * maximum number of concurrent in-flight calls it has ever observed.
     *
     * Used to verify that the lock decorator actually serializes execution.
     */
    class CountingRouter(private val sleepMillis: Long = 0) : InboundRouter {
        val callCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        override fun execute(request: UnifiedRequest): UnifiedResponse = runBlocking {
            executeSuspend(request)
        }

        override suspend fun executeSuspend(request: UnifiedRequest): UnifiedResponse {
            val current = callCount.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, current) }
            if (sleepMillis > 0) Thread.sleep(sleepMillis)
            callCount.decrementAndGet()
            return UnifiedResponse(success = true, data = "ok")
        }
    }
}
