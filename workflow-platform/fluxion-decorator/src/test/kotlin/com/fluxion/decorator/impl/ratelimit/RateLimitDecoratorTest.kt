package com.fluxion.decorator.impl.ratelimit

import com.fluxion.core.exception.RateLimitExceededException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.ratelimit.RateLimitConfig
import com.fluxion.core.ratelimit.RateLimitStore
import com.fluxion.core.value.FunctionResult
import com.fluxion.decorator.impl.RateLimitDecorator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RateLimitDecoratorTest {

    private val config = RateLimitConfig(upLimited = 2, cdSeconds = 10, recoveryPerCd = 2)

    @Test
    fun `should throw when limit exceeded`() {
        val store = object : RateLimitStore {
            private var count = 0
            override fun tryAcquire(key: String, config: RateLimitConfig): Boolean {
                count++
                return count <= 2
            }
        }
        val decorator = RateLimitDecorator(store)
        val node = WorkflowNode(id = "n1", name = "node1", workflowId = "wf1", functionRef = "test")
        val decorated = decorator.decorate(WorkflowFunction { FunctionResult.success("ok") }, node)

        assertEquals("ok", decorated.apply(emptyInput()).output)
        assertEquals("ok", decorated.apply(emptyInput()).output)
        assertThrows(RateLimitExceededException::class.java) { decorated.apply(emptyInput()) }
    }

    @Test
    fun `default store should be local`() {
        val decorator = RateLimitDecorator()
        assertEquals("ratelimit:slidingWindow", decorator.name())
    }

    private fun emptyInput() = com.fluxion.core.model.NodeInput(directInput = null)
}
