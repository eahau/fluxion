package com.fluxion.core.ratelimit

import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class WorkflowRateLimitDecoratorTest {

    private val store = FixedWindowRateLimitStore()

    @Test
    fun `should allow requests within limit`() = runBlocking {
        val decorator = WorkflowRateLimitDecorator(store)
        val def = WorkflowDefinition().apply {
            id = "wf-rl-1"
            workflowDecoratorParams = mapOf("workflow:ratelimit" to mapOf("upLimited" to 2, "cdSeconds" to 10))
        }

        val r1 = decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }
        val r2 = decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }

        assertEquals("ok", r1.data)
        assertEquals("ok", r2.data)
    }

    @Test
    fun `should reject requests exceeding limit`() = runBlocking {
        val decorator = WorkflowRateLimitDecorator(store)
        val def = WorkflowDefinition().apply {
            id = "wf-rl-2"
            workflowDecoratorParams = mapOf("workflow:ratelimit" to mapOf("upLimited" to 1, "cdSeconds" to 10))
        }

        decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }

        assertThrows(RateLimitExceededException::class.java) {
            runBlocking {
                decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }
            }
        }
    }

    @Test
    fun `should use explicit rate limit key`() = runBlocking {
        val decorator = WorkflowRateLimitDecorator(store)
        val def = WorkflowDefinition().apply {
            id = "wf-rl-3"
            workflowDecoratorParams = mapOf(
                "workflow:ratelimit" to mapOf(
                    "upLimited" to 1,
                    "cdSeconds" to 10,
                    "rateLimitKey" to "shared-bucket"
                )
            )
        }

        decorator.decorate(def, emptyMap()) { EngineResult.success("ok", mockState()) }
        assertEquals(1, store.getCount("{fluxion:wf-rl-3}:shared-bucket"))
    }

    private fun mockState() = com.fluxion.core.model.ImmutableExecutionState.start(
        WorkflowDefinition().apply { id = "mock" },
        emptyMap()
    )

    private class FixedWindowRateLimitStore : RateLimitStore {
        private val counters = ConcurrentHashMap<String, AtomicInteger>()

        override fun tryAcquire(key: String, config: RateLimitConfig): Boolean {
            val count = counters.computeIfAbsent(key) { AtomicInteger(0) }
            return count.incrementAndGet() <= config.upLimited
        }

        fun getCount(key: String): Int = counters[key]?.get() ?: 0
    }
}
