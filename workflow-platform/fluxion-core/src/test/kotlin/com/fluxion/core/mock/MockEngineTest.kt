package com.fluxion.core.mock

import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.ExecutionMeta
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MockEngineTest {

    private fun nodeInput(
        directInput: Any? = null,
        workflowInput: Map<String, Any> = emptyMap(),
        declaredDeps: Map<String, Any> = emptyMap(),
        nodeParams: Map<String, Any> = emptyMap()
    ) = NodeInput(
        directInput = directInput,
        workflowInput = workflowInput,
        declaredDeps = declaredDeps,
        nodeParams = nodeParams,
        meta = ExecutionMeta(
            workflowId = "wf-test",
            workflowName = "test",
            version = 1,
            executionId = "exec-1",
            startTime = 0L
        )
    )

    @Test
    fun `static response hit`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    response = mapOf("ok" to true)
                )
            )
        )

        val result = MockEngine.evaluate(config, "custom:demo", nodeInput())
        assertEquals(mapOf("ok" to true), result)
    }

    @Test
    fun `functionRef mismatch returns null`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(id = "r1", functionRef = "custom:a", response = mapOf("ok" to true))
            )
        )

        assertNull(MockEngine.evaluate(config, "custom:b", nodeInput()))
    }

    @Test
    fun `disabled config returns null`() = runBlocking {
        val config = MockConfig(
            enabled = false,
            rules = listOf(
                MockRule(id = "r1", functionRef = "custom:demo", response = mapOf("ok" to true))
            )
        )

        assertNull(MockEngine.evaluate(config, "custom:demo", nodeInput()))
    }

    @Test
    fun `disabled rule returns null`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    enabled = false,
                    response = mapOf("ok" to true)
                )
            )
        )

        assertNull(MockEngine.evaluate(config, "custom:demo", nodeInput()))
    }

    @Test
    fun `field matcher hit`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    condition = MockCondition(
                        fieldMatchers = mapOf(
                            "directInput.userId" to FieldMatcher(exact = setOf("test_001"))
                        )
                    ),
                    response = mapOf("mocked" to true)
                )
            )
        )

        val input = nodeInput(directInput = mapOf("userId" to "test_001", "name" to "foo"))
        assertEquals(mapOf("mocked" to true), MockEngine.evaluate(config, "custom:demo", input))

        val miss = nodeInput(directInput = mapOf("userId" to "other"))
        assertNull(MockEngine.evaluate(config, "custom:demo", miss))
    }

    @Test
    fun `jexl condition hit`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    condition = MockCondition(expression = "workflowInput.env == 'local'"),
                    response = mapOf("mocked" to true)
                )
            )
        )

        val hit = nodeInput(workflowInput = mapOf("env" to "local"))
        assertEquals(mapOf("mocked" to true), MockEngine.evaluate(config, "custom:demo", hit))

        val miss = nodeInput(workflowInput = mapOf("env" to "prod"))
        assertNull(MockEngine.evaluate(config, "custom:demo", miss))
    }

    @Test
    fun `template with placeholder`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    responseTemplate = """{"orderId":"${'$'}{directInput.orderId}","status":"ok"}"""
                )
            )
        )

        val input = nodeInput(directInput = mapOf("orderId" to "ORD-123"))
        val result = MockEngine.evaluate(config, "custom:demo", input) as Map<*, *>
        assertEquals("ORD-123", result["orderId"])
        assertEquals("ok", result["status"])
    }

    @Test
    fun `template with default placeholder`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    responseTemplate = """{"name":"${'$'}{directInput.name:guest}"}"""
                )
            )
        )

        val input = nodeInput(directInput = emptyMap<String, Any>())
        val result = MockEngine.evaluate(config, "custom:demo", input) as Map<*, *>
        assertEquals("guest", result["name"])
    }

    @Test
    fun `renderTemplate with fn functions`() = runBlocking {
        val rendered = MockEngine.renderTemplate(
            """{"ts":#{fn.now()},"uuid":"#{fn.uuidShort()}"}""",
            nodeInput()
        )
        assertTrue(rendered.matches(Regex("""\{"ts":\d+,"uuid":"[0-9a-f]{32}"\}""")))
    }

    @Test
    fun `template with jexl function`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    responseTemplate = """{"ts":#{fn.now()},"uuid":"#{fn.uuidShort()}"}"""
                )
            )
        )

        val raw = MockEngine.evaluate(config, "custom:demo", nodeInput())
        if (raw !is Map<*, *>) {
            throw AssertionError("expected Map but got ${raw?.javaClass}: $raw")
        }
        val result = raw as Map<*, *>
        assertTrue((result["ts"] as Number).toLong() > 0)
        assertEquals(32, (result["uuid"] as String).length)
    }

    @Test
    fun `static response is deep copied`() = runBlocking {
        val config = MockConfig(
            rules = listOf(
                MockRule(
                    id = "r1",
                    functionRef = "custom:demo",
                    response = mutableMapOf("counter" to 0)
                )
            )
        )

        val result1 = MockEngine.evaluate(config, "custom:demo", nodeInput()) as MutableMap<*, *>
        result1.uncheckedCast<MutableMap<String, Int>>()!!["counter"] = 99

        val result2 = MockEngine.evaluate(config, "custom:demo", nodeInput()) as Map<*, *>
        assertEquals(0, result2["counter"])
    }

    @Test
    fun `flatten node input`() = runBlocking {
        val input = nodeInput(
            directInput = mapOf("a" to 1),
            workflowInput = mapOf("b" to mapOf("c" to "deep")),
            declaredDeps = mapOf("n1" to mapOf("d" to "dep")),
            nodeParams = mapOf("p" to "param")
        )

        val flat = MockEngine.flattenNodeInput(input)
        assertEquals("1", flat["directInput.a"])
        assertEquals("deep", flat["workflowInput.b.c"])
        assertEquals("dep", flat["declaredDeps.n1.d"])
        assertEquals("param", flat["nodeParams.p"])
    }
}
