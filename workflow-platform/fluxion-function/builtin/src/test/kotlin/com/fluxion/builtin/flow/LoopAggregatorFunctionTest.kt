package com.fluxion.builtin.flow

import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LoopAggregatorFunctionTest {

    private val function = LoopAggregatorFunction()

    @Test
    fun `linear flow list input`() {
        val input = NodeInput(
            directInput = listOf(mapOf("a" to 1), mapOf("b" to 2)),
            nodeParams = mapOf("mode" to "merge")
        )
        val result = function.apply(input)

        val merged = result.output.uncheckedCast<Map<String, Any>>()!!
        assertEquals(1, merged["a"])
        assertEquals(2, merged["b"])
    }

    @Test
    fun `DAG merge from declaredDeps`() {
        val input = NodeInput(
            directInput = null,
            declaredDeps = mapOf(
                "fetchUser" to mapOf("userId" to 100),
                "fetchOrder" to mapOf("orderId" to 200)
            ),
            nodeParams = mapOf("mode" to "merge")
        )
        val result = function.apply(input)

        val merged = result.output.uncheckedCast<Map<String, Any>>()!!
        assertEquals(100, merged["userId"])
        assertEquals(200, merged["orderId"])
    }

    @Test
    fun `DAG list mode preserves dependency order`() {
        val input = NodeInput(
            directInput = null,
            declaredDeps = linkedMapOf(
                "branchA" to "first",
                "branchB" to "second"
            ),
            nodeParams = mapOf("mode" to "list")
        )
        val result = function.apply(input)

        val list = result.output.uncheckedCast<List<*>>()!!
        assertEquals(listOf("first", "second"), list)
    }

    @Test
    fun `first mode`() {
        val input = NodeInput(
            directInput = null,
            declaredDeps = mapOf(
                "a" to null,
                "b" to "hit"
            ).uncheckedCast<Map<String, Any>>()!!,
            nodeParams = mapOf("mode" to "first")
        )
        val result = function.apply(input)
        assertEquals("hit", result.output)
    }

    @Test
    fun `declaredDeps takes precedence over directInput`() {
        val input = NodeInput(
            directInput = listOf("ignored"),
            declaredDeps = mapOf("dep" to "used"),
            nodeParams = mapOf("mode" to "list")
        )
        val result = function.apply(input)

        val list = result.output.uncheckedCast<List<*>>()!!
        assertEquals(listOf("used"), list)
    }
}
