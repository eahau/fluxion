/**
 * Tests covering [FunctionRegistry] lookup semantics, version hot-swap,
 * in-flight retention, and namespace-prefix fallback behaviour.
 *
 * The suite focuses on concurrency-sensitive paths: a long-running in-flight
 * invocation on v1 must complete successfully even after v2 is published and
 * v1 moves into the RETIRING slot, and retiring slots must only be purged
 * after the in-flight counter drains to zero.
 */
package com.fluxion.core.function

import com.fluxion.core.enums.NodeType
import com.fluxion.core.exception.FunctionNotFoundException
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import com.fluxion.core.value.InlineMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Behavioural tests for [FunctionRegistry] and the embedded [VersionedFunction]
 * two-slot state machine.
 */
class FunctionRegistryTest {

    private val registry = FunctionRegistry()

    @Test
    fun `register and resolve code function`() {
        val fn = constantFunction("hello")
        registry.register("demo:hello", fn)

        val resolved = registry.resolve("demo:hello")
        assertEquals("hello", resolved.apply(emptyInput()).output)
    }

    @Test
    fun `code function with version 0 overwrites without retiring`() {
        registry.register("builtin:test", InlineMeta.of("builtin:test"), constantFunction("v1"))
        registry.register("builtin:test", InlineMeta.of("builtin:test"), constantFunction("v2"))

        assertNull(registry.retiringVersion("builtin:test"))
        assertEquals(0L, registry.activeVersion("builtin:test"))
        assertEquals("v2", registry.resolve("builtin:test").apply(emptyInput()).output)
    }

    @Test
    fun `hot-published function retires previous version`() {
        registry.register("script:calc", 1, InlineMeta.of("script:calc"), constantFunction("v1"))
        registry.register("script:calc", 2, InlineMeta.of("script:calc"), constantFunction("v2"))

        assertEquals(2L, registry.activeVersion("script:calc"))
        assertEquals(1L, registry.retiringVersion("script:calc"))
        assertEquals("v2", registry.resolve("script:calc").apply(emptyInput()).output)
    }

    @Test
    fun `in-flight call on retiring version continues after new version published`() {
        val latch = CountDownLatch(1)
        val started = CountDownLatch(1)

        // v1 blocks inside apply() until latch is counted down, simulating a
        // slow downstream RPC that spans a version hot-swap.
        val v1 = blockingFunction(started, latch, "v1-result")
        registry.register("script:long", 1, InlineMeta.of("script:long"), v1)

        val executor = Executors.newSingleThreadExecutor()
        val future: Future<FunctionResult<Any>> = executor.submit(Callable {
            registry.resolve("script:long").apply(emptyInput())
        })

        // Wait until v1 has entered apply so the version pin is established.
        started.await(1, TimeUnit.SECONDS)

        // Publish v2 while v1 is still in-flight; v1 must move to RETIRING
        // rather than being discarded so the pinned invocation can finish.
        registry.register("script:long", 2, InlineMeta.of("script:long"), constantFunction("v2-result"))
        assertEquals(2L, registry.activeVersion("script:long"))
        assertEquals(1L, registry.retiringVersion("script:long"))

        // New lookups after the swap must observe v2 immediately.
        assertEquals("v2-result", registry.resolve("script:long").apply(emptyInput()).output)

        // Unblock v1 and assert the pinned invocation returned cleanly.
        latch.countDown()
        val v1Result = future.get(1, TimeUnit.SECONDS)
        assertEquals("v1-result", v1Result.output)

        executor.shutdown()
    }

    @Test
    fun `retiring version can be purged after in-flight ends`() {
        registry.register("script:x", 1, InlineMeta.of("script:x"), constantFunction("v1"))
        registry.register("script:x", 2, InlineMeta.of("script:x"), constantFunction("v2"))

        assertEquals(1L, registry.retiringVersion("script:x"))

        // With zero in-flight calls the first purge call must drop the slot
        // and return true so housekeeping stats can be aggregated accurately.
        assertTrue(registry.purgeRetiring("script:x"))
        assertNull(registry.retiringVersion("script:x"))

        // A second purge on an already-empty slot must be a no-op returning
        // false, matching the documented idempotent semantics.
        assertFalse(registry.purgeRetiring("script:x"))
    }

    @Test
    fun `resolve specific version from active or retiring`() {
        registry.register("script:y", 1, InlineMeta.of("script:y"), constantFunction("v1"))
        registry.register("script:y", 2, InlineMeta.of("script:y"), constantFunction("v2"))

        assertEquals("v2", registry.resolve("script:y", null, 2).apply(emptyInput()).output)
        assertEquals("v1", registry.resolve("script:y", null, 1).apply(emptyInput()).output)
    }

    @Test
    fun `unregister removes all versions`() {
        registry.register("script:z", 1, InlineMeta.of("script:z"), constantFunction("v1"))
        registry.register("script:z", 2, InlineMeta.of("script:z"), constantFunction("v2"))

        registry.unregister("script:z")

        assertFalse(registry.contains("script:z"))
        assertThrows<FunctionNotFoundException> { registry.resolve("script:z") }
    }

    @Test
    fun `resolve with node type prefix fallback`() {
        registry.register("script:prefixed", InlineMeta.of("script:prefixed"), constantFunction("ok"))

        val resolved = registry.resolve("prefixed", NodeType.SCRIPT)
        assertEquals("ok", resolved.apply(emptyInput()).output)
    }

    // --- test helpers ---

    /**
     * Returns a [WorkflowFunction] that always emits the supplied string.
     *
     * @param output constant value returned from [WorkflowFunction.apply]
     */
    private fun constantFunction(output: String): WorkflowFunction<Any> =
        object : WorkflowFunction<Any> {
            override fun apply(input: NodeInput): FunctionResult<Any> =
                FunctionResult.success(output)

            override val functionName = "constant"
        }

    /**
     * Returns a [WorkflowFunction] that blocks inside apply until [latch] is
     * counted down, signalling readiness via [started] first.
     *
     * Used to reproduce race conditions where a version swap happens mid-call.
     *
     * @param started counted down immediately on entry to apply()
     * @param latch caller awaits this latch inside apply()
     * @param output value returned once the latch is released
     */
    private fun blockingFunction(
        started: CountDownLatch,
        latch: CountDownLatch,
        output: String
    ): WorkflowFunction<Any> =
        object : WorkflowFunction<Any> {
            override fun apply(input: NodeInput): FunctionResult<Any> {
                started.countDown()
                latch.await(5, TimeUnit.SECONDS)
                return FunctionResult.success(output)
            }

            override val functionName = "blocking"
        }

    /** Convenience helper returning an empty [NodeInput] snapshot. */
    private fun emptyInput() = NodeInput(null)
}
