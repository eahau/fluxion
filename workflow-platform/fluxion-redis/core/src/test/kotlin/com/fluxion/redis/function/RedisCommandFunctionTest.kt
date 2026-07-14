package com.fluxion.redis.function

import com.fluxion.core.model.NodeInput
import com.fluxion.redis.spi.RedisClientAdapter
import com.fluxion.redis.spi.RedisRawCommand
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RedisCommandFunction].
 *
 * Covers the two parameter modes (raw CLI string vs structured command/key/args),
 * template variable resolution, EVAL script key/args splitting, quoted argument
 * tokenization, and the "raw takes precedence over structured" dispatch rule.
 * All assertions use a fake in-memory [RedisClientAdapter] so no Redis server is needed.
 */
class RedisCommandFunctionTest {

    private val fakeAdapter = FakeRedisAdapter()
    private val function = RedisCommandFunction(fakeAdapter)

    @Test
    fun `raw mode parses simple SET command`() {
        val input = nodeInput(raw = "SET user:1001 name John")

        function.apply(input)

        assertEquals("SET", fakeAdapter.lastCommand)
        assertEquals("{myApp:wf1}:user:1001", fakeAdapter.lastKey)
        assertEquals(listOf("name", "John"), fakeAdapter.lastArgs)
    }

    @Test
    fun `raw mode supports quoted arguments with spaces`() {
        val input = nodeInput(raw = "SET user:1001 name \"John Doe\"")

        function.apply(input)

        assertEquals("SET", fakeAdapter.lastCommand)
        assertEquals("{myApp:wf1}:user:1001", fakeAdapter.lastKey)
        assertEquals(listOf("name", "John Doe"), fakeAdapter.lastArgs)
    }

    @Test
    fun `raw mode supports single quoted arguments`() {
        val input = nodeInput(raw = "SET user:1001 name 'John Doe'")

        function.apply(input)

        assertEquals(listOf("name", "John Doe"), fakeAdapter.lastArgs)
    }

    @Test
    fun `raw mode resolves template variables`() {
        val input = nodeInput(
            raw = "SET user:\${userId} name \${userName}",
            workflowInput = mapOf("userId" to "42", "userName" to "Alice")
        )

        function.apply(input)

        assertEquals("SET", fakeAdapter.lastCommand)
        assertEquals("{myApp:wf1}:user:42", fakeAdapter.lastKey)
        assertEquals(listOf("name", "Alice"), fakeAdapter.lastArgs)
    }

    @Test
    fun `raw mode EVAL command parses script keys and args`() {
        val input = nodeInput(raw = "EVAL \"return KEYS[1]\" 1 key1 arg1")

        function.apply(input)

        assertEquals("return KEYS[1]", fakeAdapter.lastEvalScript)
        assertEquals(listOf("{myApp:wf1}:key1"), fakeAdapter.lastEvalKeys)
        assertEquals(listOf("arg1"), fakeAdapter.lastEvalArgs)
    }

    @Test
    fun `raw mode EVAL with multiple keys and args`() {
        val input = nodeInput(raw = "EVAL script 2 k1 k2 a1 a2")

        function.apply(input)

        assertEquals("script", fakeAdapter.lastEvalScript)
        assertEquals(listOf("{myApp:wf1}:k1", "{myApp:wf1}:k2"), fakeAdapter.lastEvalKeys)
        assertEquals(listOf("a1", "a2"), fakeAdapter.lastEvalArgs)
    }

    @Test
    fun `structured mode still works when raw is blank`() {
        val input = nodeInput(
            command = "GET",
            key = "user:1001",
            args = listOf("field")
        )

        function.apply(input)

        assertEquals("GET", fakeAdapter.lastCommand)
        assertEquals("{myApp:wf1}:user:1001", fakeAdapter.lastKey)
        assertEquals(listOf("field"), fakeAdapter.lastArgs)
    }

    @Test
    fun `raw mode takes precedence over structured params`() {
        val input = nodeInput(
            raw = "DEL user:1001",
            command = "GET",
            key = "ignored",
            args = listOf("ignored")
        )

        function.apply(input)

        assertEquals("DEL", fakeAdapter.lastCommand)
        assertEquals("{myApp:wf1}:user:1001", fakeAdapter.lastKey)
        assertTrue(fakeAdapter.lastArgs.isEmpty())
    }

    private fun nodeInput(
        raw: String? = null,
        command: String? = null,
        key: Any? = null,
        args: List<Any>? = null,
        workflowInput: Map<String, Any> = emptyMap(),
        appGroup: String = "myApp",
        workflowId: String = "wf1"
    ): NodeInput = NodeInput(
        directInput = null,
        workflowInput = workflowInput,
        nodeParams = buildMap {
            raw?.let { put("raw", it) }
            command?.let { put("command", it) }
            key?.let { put("key", it) }
            args?.let { put("args", it) }
        },
        meta = com.fluxion.core.value.ExecutionMeta(
            workflowId = workflowId,
            workflowName = "test",
            version = 1,
            appGroup = appGroup,
            executionId = "exec-1",
            startTime = System.currentTimeMillis()
        )
    )

    private class FakeRedisAdapter : RedisClientAdapter {
        var lastCommand: String? = null
        var lastKey: String? = null
        var lastArgs: List<String> = emptyList()
        var lastEvalScript: String? = null
        var lastEvalKeys: List<String> = emptyList()
        var lastEvalArgs: List<String> = emptyList()

        override fun execute(command: String, key: String, args: List<String>): Any? {
            lastCommand = command
            lastKey = key
            lastArgs = args
            return null
        }

        override fun pipeline(commands: List<RedisRawCommand>): List<Any?> {
            throw UnsupportedOperationException("Not used in tests")
        }

        override fun eval(script: String, keys: List<String>, args: List<String>): Any? {
            lastEvalScript = script
            lastEvalKeys = keys
            lastEvalArgs = args
            return null
        }

        override fun scriptLoad(script: String): String = "sha:${script.hashCode()}"

        override fun evalSha(sha: String, keys: List<String>, args: List<String>): Any? {
            lastEvalKeys = keys
            lastEvalArgs = args
            return null
        }
    }
}
