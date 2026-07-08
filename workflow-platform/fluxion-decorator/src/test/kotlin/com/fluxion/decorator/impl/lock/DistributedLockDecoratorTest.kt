package com.fluxion.decorator.impl.lock

import com.fluxion.core.exception.LockAcquisitionException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import com.fluxion.test.lock.InMemoryDistributedLockProvider
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.FunctionResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class DistributedLockDecoratorTest {

    private lateinit var lockProvider: InMemoryDistributedLockProvider

    private val expectedLockKey = "{fluxion:wf1}:test:lock"

    @BeforeEach
    fun setUp() {
        lockProvider = InMemoryDistributedLockProvider()
    }

    @Test
    fun `should acquire and release lock around function execution`() {
        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf(
                "lock:distributed" to mapOf(
                    "lockKey" to "test:lock",
                    "leaseMillis" to 5000L
                )
            )
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction { FunctionResult.success("ok") },
            node
        )

        val result = decorated.apply(nodeInput())

        assertEquals("ok", result.output)
        assertTrue(lockProvider.isReleased(expectedLockKey))
    }

    @Test
    fun `should throw LockAcquisitionException when lock is held and failOnLocked is true`() {
        lockProvider.acquire(expectedLockKey, 5000L, 0L)

        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf(
                "lock:distributed" to mapOf("lockKey" to expectedLockKey)
            )
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction { FunctionResult.success("ok") },
            node
        )

        assertThrows(LockAcquisitionException::class.java) {
            decorated.apply(nodeInput())
        }
    }

    @Test
    fun `should pass through without exception when failOnLocked is false`() {
        lockProvider.acquire(expectedLockKey, 5000L, 0L)

        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf(
                "lock:distributed" to mapOf(
                    "lockKey" to "test:lock",
                    "failOnLocked" to false
                )
            )
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction { FunctionResult.success("fallback-ok") },
            node
        )

        val result = decorated.apply(nodeInput())
        assertEquals("fallback-ok", result.output)
    }

    @Test
    fun `should release lock even if function throws`() {
        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf("lock:distributed" to mapOf("lockKey" to "test:lock"))
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction { error("boom") },
            node
        )

        assertThrows(IllegalStateException::class.java) {
            decorated.apply(nodeInput())
        }
        assertTrue(lockProvider.isReleased(expectedLockKey))
    }

    @Test
    fun `should succeed after transient failures with retry`() {
        lockProvider.transientFailures[expectedLockKey] = AtomicInteger(2)

        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf(
                "lock:distributed" to mapOf(
                    "lockKey" to "test:lock",
                    "retry" to 5,
                    "retryIntervalMillis" to 10L
                )
            )
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction { FunctionResult.success("ok") },
            node
        )

        val result = decorated.apply(nodeInput())

        assertEquals("ok", result.output)
        assertEquals(5, lockProvider.lastRetry)
        assertEquals(10L, lockProvider.lastRetryIntervalMillis)
        assertTrue(lockProvider.acquireAttempts[expectedLockKey]?.get() ?: 0 >= 3)
    }

    @Test
    fun `should serialize concurrent executions with same lock key`() {
        val counter = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "test",
            decorators = listOf("lock:distributed"),
            decoratorParams = mapOf("lock:distributed" to mapOf("lockKey" to "test:lock"))
        )
        val decorated = DistributedLockDecorator(lockProvider).decorate(
            WorkflowFunction {
                val current = counter.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                Thread.sleep(10)
                counter.decrementAndGet()
                FunctionResult.success("ok")
            },
            node
        )

        val threads = (1..5).map {
            Thread { decorated.apply(nodeInput()) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, maxConcurrent.get())
    }

    private fun nodeInput() = com.fluxion.core.model.NodeInput(
        directInput = emptyMap<String, Any>(),
        meta = ExecutionMeta(
            workflowId = "wf1",
            workflowName = "wf",
            version = 1,
            executionId = "exec-1",
            startTime = System.currentTimeMillis()
        )
    )
}
