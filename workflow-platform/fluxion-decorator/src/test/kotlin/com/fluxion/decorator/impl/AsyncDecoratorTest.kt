package com.fluxion.decorator.impl

import com.fluxion.core.decorator.AsyncCallback
import com.fluxion.core.decorator.AsyncCallbackContext
import com.fluxion.core.decorator.AsyncCallbackStatus
import com.fluxion.core.engine.TaskInterceptor
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.core.value.FunctionResult
import com.fluxion.decorator.decorator.AsyncDecorator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AsyncDecoratorTest {

    private val executor = Executors.newSingleThreadExecutor()

    @Test
    fun `should return asyncId immediately and callback with success context`() {
        val latch = CountDownLatch(1)
        val callback = RecordingAsyncCallback(latch)
        val decorator = AsyncDecorator(executor, callback, TaskInterceptor.NOOP)
        val node = WorkflowNode(
            id = "n1",
            name = "node1",
            workflowId = "wf1",
            functionRef = "builtin:httpCall"
        )
        val meta = ExecutionMeta.builder()
            .workflowId("wf1")
            .workflowName("testWorkflow")
            .executionId("exec-123")
            .appGroup("app1")
            .startTime(System.currentTimeMillis())
            .build()
        val input = NodeInput(directInput = mapOf("key" to "value"), meta = meta)
        val decorated = decorator.decorate(WorkflowFunction { FunctionResult.success("done") }, node)

        val result = decorated.apply(input)

        @Suppress("UNCHECKED_CAST")
        val output = result.output as Map<String, Any>
        assertEquals("PENDING", output["status"])
        assertEquals("exec-123", output["executionId"])
        assertNotNull(output["asyncId"])

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(1, callback.contexts.size)

        val ctx = callback.contexts.first()
        assertEquals(output["asyncId"], ctx.asyncId)
        assertEquals("exec-123", ctx.executionId)
        assertEquals("wf1", ctx.workflowId)
        assertEquals("testWorkflow", ctx.workflowName)
        assertEquals("app1", ctx.appGroup)
        assertEquals("n1", ctx.nodeId)
        assertEquals("node1", ctx.nodeName)
        assertEquals("builtin:httpCall", ctx.functionRef)
        assertEquals(AsyncCallbackStatus.SUCCESS, ctx.status)
        assertEquals("done", ctx.output)
        assertNull(ctx.errorMsg)
    }

    @Test
    fun `should callback with failed context when function throws exception`() {
        val latch = CountDownLatch(1)
        val callback = RecordingAsyncCallback(latch)
        val decorator = AsyncDecorator(executor, callback, TaskInterceptor.NOOP)
        val node = WorkflowNode(id = "n2", name = "node2", workflowId = "wf2", functionRef = "test")
        val input = NodeInput(directInput = null)
        val decorated = decorator.decorate(WorkflowFunction { throw RuntimeException("boom") }, node)

        val result = decorated.apply(input)

        @Suppress("UNCHECKED_CAST")
        val asyncId = (result.output as Map<String, Any>)["asyncId"] as String

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        val ctx = callback.contexts.first()
        assertEquals(asyncId, ctx.asyncId)
        assertEquals("n2", ctx.nodeId)
        assertEquals("node2", ctx.nodeName)
        assertEquals("wf2", ctx.workflowId)
        assertEquals(AsyncCallbackStatus.FAILED, ctx.status)
        assertNull(ctx.output)
        assertEquals("boom", ctx.errorMsg)
    }

    @Test
    fun `should be thread-safe for concurrent async executions`() {
        val concurrent = 20
        val latch = CountDownLatch(concurrent)
        val callback = RecordingAsyncCallback(latch)
        val pool = Executors.newFixedThreadPool(4)
        val decorator = AsyncDecorator(pool, callback, TaskInterceptor.NOOP)
        val node = WorkflowNode(id = "n3", name = "node3", workflowId = "wf3", functionRef = "test")
        val decorated = decorator.decorate(
            WorkflowFunction {
                Thread.sleep((Math.random() * 20).toLong())
                FunctionResult.success("ok")
            },
            node
        )
        val input = NodeInput(directInput = null)

        val asyncIds = ConcurrentHashMap.newKeySet<String>()
        repeat(concurrent) {
            val result = decorated.apply(input)
            @Suppress("UNCHECKED_CAST")
            asyncIds.add((result.output as Map<String, Any>)["asyncId"] as String)
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(concurrent, asyncIds.size)
        assertEquals(concurrent, callback.contexts.size)

        val callbackAsyncIds = callback.contexts.map { it.asyncId }.toSet()
        assertEquals(asyncIds, callbackAsyncIds)
        assertTrue(callback.contexts.all { it.status == AsyncCallbackStatus.SUCCESS })

        pool.shutdown()
    }

    @Test
    fun `should fallback to node workflowId when meta is missing`() {
        val latch = CountDownLatch(1)
        val callback = RecordingAsyncCallback(latch)
        val decorator = AsyncDecorator(executor, callback, TaskInterceptor.NOOP)
        val node = WorkflowNode(id = "n4", name = "node4", workflowId = "wf-from-node", functionRef = "test")
        val input = NodeInput(directInput = null)
        val decorated = decorator.decorate(WorkflowFunction { FunctionResult.success("ok") }, node)

        decorated.apply(input)
        assertTrue(latch.await(2, TimeUnit.SECONDS))

        val ctx = callback.contexts.first()
        assertEquals("wf-from-node", ctx.workflowId)
        assertEquals("", ctx.executionId)
    }

    private class RecordingAsyncCallback(private val latch: CountDownLatch? = null) : AsyncCallback {
        val contexts = mutableListOf<AsyncCallbackContext>()
        val publishCount = AtomicInteger(0)

        override fun publish(context: AsyncCallbackContext) {
            synchronized(contexts) {
                contexts.add(context)
            }
            publishCount.incrementAndGet()
            latch?.countDown()
        }
    }
}
