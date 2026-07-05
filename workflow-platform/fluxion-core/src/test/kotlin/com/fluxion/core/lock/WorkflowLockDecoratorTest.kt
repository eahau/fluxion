package com.fluxion.core.lock

import com.fluxion.core.exception.LockAcquisitionException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class WorkflowLockDecoratorTest {

    private val lockProvider = RecordingLockProvider()

    @Test
    fun `should acquire and release lock around workflow execution`() = runBlocking {
        val decorator = WorkflowLockDecorator(lockProvider)
        val def = WorkflowDefinition().apply { id = "wf-lock-1" }
        var executed = false

        val result = decorator.decorate(def, emptyMap()) {
            executed = true
            EngineResult.success("ok", mockState())
        }

        assertTrue(executed)
        assertEquals("ok", result.data)
        assertEquals(1, lockProvider.acquireCount)
        assertEquals(1, lockProvider.releaseCount)
        assertTrue(lockProvider.lastKey?.contains("wf-lock-1") == true)
    }

    @Test
    fun `should throw LockAcquisitionException when failOnLocked is true`() = runBlocking {
        lockProvider.alwaysFail = true
        val decorator = WorkflowLockDecorator(lockProvider)
        val def = WorkflowDefinition().apply {
            id = "wf-lock-2"
            workflowDecoratorParams = mapOf("workflow:lock" to mapOf("failOnLocked" to true))
        }

        assertThrows(LockAcquisitionException::class.java) {
            runBlocking {
                decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }
            }
        }
    }

    @Test
    fun `should execute without lock when failOnLocked is false`() = runBlocking {
        lockProvider.alwaysFail = true
        val decorator = WorkflowLockDecorator(lockProvider)
        val def = WorkflowDefinition().apply {
            id = "wf-lock-3"
            workflowDecoratorParams = mapOf("workflow:lock" to mapOf("failOnLocked" to false))
        }

        val result = decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }
        assertEquals("ok", result.data)
    }

    @Test
    fun `should evaluate lock key expression`() = runBlocking {
        val decorator = WorkflowLockDecorator(lockProvider)
        val def = WorkflowDefinition().apply {
            id = "wf-lock-4"
            workflowDecoratorParams = mapOf(
                "workflow:lock" to mapOf("lockKeyExpression" to "lock:${'$'}{workflowId}:${'$'}{input.userId}")
            )
        }

        decorator.decorate(def, mapOf("userId" to 42)) { EngineResult.success("ok", mockState()) }
        assertEquals("{fluxion:wf-lock-4}:lock:wf-lock-4:42", lockProvider.lastKey)
    }

    private fun mockState() = com.fluxion.core.model.ImmutableExecutionState.start(
        WorkflowDefinition().apply { id = "mock" },
        emptyMap()
    )

    private class RecordingLockProvider : DistributedLockProvider {
        var acquireCount = 0
        var releaseCount = 0
        var lastKey: String? = null
        var alwaysFail = false
        private val locks = ConcurrentHashMap<String, DistributedLock>()

        override fun acquire(
            lockKey: String,
            leaseMillis: Long,
            waitMillis: Long,
            retry: Int,
            retryIntervalMillis: Long,
            sync: Boolean
        ): DistributedLock? {
            acquireCount++
            lastKey = lockKey
            if (alwaysFail) return null
            return locks.computeIfAbsent(lockKey) { MemoryLock(it, AtomicInteger(0)) }
        }

        override fun release(lock: DistributedLock) {
            releaseCount++
            locks.remove(lock.key)
        }
    }

    private class MemoryLock(override val key: String, val counter: AtomicInteger) : DistributedLock {
        override val token: String = "token-$key"
    }
}
