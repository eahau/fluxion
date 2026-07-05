package com.fluxion.core.model

import com.fluxion.core.exception.InvalidParamException
import com.fluxion.core.value.ExecutionMeta
import com.fluxion.schema.json.JsonSchemaDataProvider
import com.fluxion.schema.api.SchemaBackedMap
import com.fluxion.schema.api.SchemaDataProviderRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NodeInputTest {

    private val meta = ExecutionMeta(
        workflowId = "wf-test",
        workflowName = "test",
        version = 1,
        executionId = "exec-1",
        startTime = 0L
    )

    private fun input(
        directInput: Any? = null,
        workflowInput: Map<String, Any> = emptyMap(),
        declaredDeps: Map<String, Any> = emptyMap(),
        nodeParams: Map<String, Any> = emptyMap()
    ) = NodeInput(
        directInput = directInput,
        workflowInput = workflowInput,
        declaredDeps = declaredDeps,
        nodeParams = nodeParams,
        meta = meta
    )

    // ─── directInput 安全访问 ────────────────────────────────────

    @Test
    fun `input returns typed value`() {
        val ni = input(directInput = "hello")
        assertEquals("hello", ni.input<String>())
    }

    @Test
    fun `input returns null for type mismatch`() {
        val ni = input(directInput = "hello")
        assertNull(ni.input<Int>())
    }

    @Test
    fun `requireInput throws on null`() {
        val ni = input(directInput = null)
        assertThrows<InvalidParamException> { ni.requireInput<String>() }
    }

    @Test
    fun `input with default value`() {
        val ni = input(directInput = null)
        assertEquals("fallback", ni.input("fallback"))
    }

    @Test
    fun `inputAsString converts any to string`() {
        val ni = input(directInput = 42)
        assertEquals("42", ni.inputAsString())
    }

    @Test
    fun `inputAsInt converts string to int`() {
        val ni = input(directInput = "123")
        assertEquals(123, ni.inputAsInt())
    }

    @Test
    fun `inputAsInt throws for non-numeric string`() {
        val ni = input(directInput = "abc")
        assertThrows<NumberFormatException> { ni.inputAsInt() }
    }

    @Test
    fun `inputAsInt returns default for null directInput`() {
        val ni = input(directInput = null)
        assertEquals(0, ni.inputAsInt())
    }

    @Test
    fun `inputAsBoolean recognizes truthy values`() {
        listOf("true", "1", "yes", "on").forEach { v ->
            assertEquals(true, input(directInput = v).inputAsBoolean(), "expected true for '$v'")
        }
    }

    @Test
    fun `inputAsBoolean recognizes falsy values`() {
        listOf("false", "0", "no", "off").forEach { v ->
            assertEquals(false, input(directInput = v).inputAsBoolean(), "expected false for '$v'")
        }
    }

    // ─── nodeParams 安全访问 ────────────────────────────────────

    @Test
    fun `param retrieves node parameter`() {
        val ni = input(nodeParams = mapOf("url" to "https://example.com"))
        assertEquals("https://example.com", ni.param<String>("url"))
    }

    @Test
    fun `requireParam throws on missing key`() {
        val ni = input(nodeParams = emptyMap())
        assertThrows<InvalidParamException> { ni.requireParam<String>("url") }
    }

    @Test
    fun `paramAsInt with default`() {
        val ni = input(nodeParams = emptyMap())
        assertEquals(5000, ni.paramAsInt("timeout", 5000))
    }

    @Test
    fun `paramAsLong converts number`() {
        val ni = input(nodeParams = mapOf("ttl" to 3600L))
        assertEquals(3600L, ni.paramAsLong("ttl"))
    }

    @Test
    fun `paramAsBoolean converts number to boolean`() {
        val ni = input(nodeParams = mapOf("flag" to 1))
        assertTrue(ni.paramAsBoolean("flag"))
    }

    // ─── workflowInput 安全访问 ──────────────────────────────────

    @Test
    fun `wfInput retrieves workflow parameter`() {
        val ni = input(workflowInput = mapOf("env" to "prod"))
        assertEquals("prod", ni.wfInput<String>("env"))
    }

    @Test
    fun `requireWfInput throws on missing key`() {
        val ni = input(workflowInput = emptyMap())
        assertThrows<InvalidParamException> { ni.requireWfInput<String>("env") }
    }

    @Test
    fun `wfInputAsInt parses string`() {
        val ni = input(workflowInput = mapOf("page" to "3"))
        assertEquals(3, ni.wfInputAsInt("page"))
    }

    // ─── declaredDeps 安全访问 ───────────────────────────────────

    @Test
    fun `dep retrieves upstream node output`() {
        val ni = input(declaredDeps = mapOf("n1" to mapOf("userId" to "u-123")))
        assertEquals(mapOf("userId" to "u-123"), ni.dep<Map<String, Any>>("n1"))
    }

    @Test
    fun `depAsString converts dep to string`() {
        val ni = input(declaredDeps = mapOf("n1" to 42))
        assertEquals("42", ni.depAsString("n1"))
    }

    @Test
    fun `requireDep throws on missing node`() {
        val ni = input(declaredDeps = emptyMap())
        assertThrows<InvalidParamException> { ni.requireDep<String>("n1") }
    }

    // ─── 模板插值 resolveBinding ─────────────────────────────────

    @Test
    fun `interpolate simple variable from workflowInput`() {
        val ni = input(workflowInput = mapOf("name" to "alice"))
        assertEquals("hello alice", ni.resolveBinding("hello \${name}"))
    }

    @Test
    fun `interpolate variable from nodeParams`() {
        val ni = input(nodeParams = mapOf("host" to "localhost"))
        assertEquals("http://localhost:8080", ni.resolveBinding("http://\${host}:8080"))
    }

    @Test
    fun `interpolate unresolved placeholder keeps original`() {
        val ni = input()
        assertEquals("hello \${unknown}", ni.resolveBinding("hello \${unknown}"))
    }

    @Test
    fun `interpolate multiple placeholders`() {
        val ni = input(workflowInput = mapOf("a" to "X", "b" to "Y"))
        assertEquals("X-Y", ni.resolveBinding("\${a}-\${b}"))
    }

    @Test
    fun `interpolate nested path from declaredDeps`() {
        val ni = input(declaredDeps = mapOf("n1" to mapOf("status" to "ok")))
        assertEquals("result=ok", ni.resolveBinding("result=\${n1.status}"))
    }

    @Test
    fun `resolveBinding null returns null`() {
        val ni = input()
        assertNull(ni.resolveBinding(null))
    }

    @Test
    fun `resolveBinding non-string returns toString`() {
        val ni = input()
        assertEquals("42", ni.resolveBinding(42))
    }

    // ─── 结构化绑定 $ref ─────────────────────────────────────────

    @Test
    fun `ref from workflowInput`() {
        val ni = input(workflowInput = mapOf("userId" to "u-001"))
        val binding = mapOf("\$ref" to "input.userId")
        assertEquals("u-001", ni.resolveBinding(binding))
    }

    @Test
    fun `ref from declaredDeps with field`() {
        val ni = input(declaredDeps = mapOf("n2" to mapOf("username" to "bob")))
        val binding = mapOf("\$ref" to "n2.username")
        assertEquals("bob", ni.resolveBinding(binding))
    }

    @Test
    fun `ref with prefix and suffix`() {
        val ni = input(workflowInput = mapOf("userId" to "u-001"))
        val binding = mapOf("\$ref" to "input.userId", "prefix" to "user:", "suffix" to ":active")
        assertEquals("user:u-001:active", ni.resolveBinding(binding))
    }

    @Test
    fun `ref bare field name fallback to workflowInput`() {
        val ni = input(workflowInput = mapOf("token" to "abc123"))
        val binding = mapOf("\$ref" to "token")
        assertEquals("abc123", ni.resolveBinding(binding))
    }

    @Test
    fun `ref bare field fallback to nodeParams`() {
        val ni = input(nodeParams = mapOf("apiKey" to "key-456"))
        val binding = mapOf("\$ref" to "apiKey")
        assertEquals("key-456", ni.resolveBinding(binding))
    }

    @Test
    fun `ref resolves to null returns null`() {
        val ni = input()
        val binding = mapOf("\$ref" to "input.nonexistent")
        assertNull(ni.resolveBinding(binding))
    }

    @Test
    fun `prefix and suffix also support template interpolation`() {
        val ni = input(workflowInput = mapOf("env" to "dev", "userId" to "u-001"))
        val binding = mapOf("\$ref" to "input.userId", "prefix" to "\${env}:")
        assertEquals("dev:u-001", ni.resolveBinding(binding))
    }

    @Test
    fun `resolveBindingList maps list of bindings`() {
        val ni = input(workflowInput = mapOf("a" to "1", "b" to "2"))
        val list = listOf("\${a}", "\${b}", "literal")
        assertEquals(listOf("1", "2", "literal"), ni.resolveBindingList(list))
    }

    // ─── 不可变更新 withXxx ──────────────────────────────────────

    @Test
    fun `withDirectInput preserves other fields`() {
        val original = input(
            directInput = "old",
            workflowInput = mapOf("k" to "v"),
            nodeParams = mapOf("p" to "1")
        )
        val updated = original.withDirectInput("new")
        assertEquals("new", updated.directInput)
        assertEquals(mapOf("k" to "v"), updated.workflowInput)
        assertEquals(mapOf("p" to "1"), updated.nodeParams)
    }

    @Test
    fun `withNodeParams replaces params`() {
        val original = input(nodeParams = mapOf("a" to "1"))
        val updated = original.withNodeParams(mapOf("b" to "2"))
        assertEquals(mapOf("b" to "2"), updated.nodeParams)
    }

    @Test
    fun `withWorkflowInput replaces input`() {
        val original = input(workflowInput = mapOf("old" to "1"))
        val updated = original.withWorkflowInput(mapOf("new" to "2"))
        assertEquals(mapOf("new" to "2"), updated.workflowInput)
    }

    @Test
    fun `withDeclaredDeps replaces deps`() {
        val original = input(declaredDeps = mapOf("n1" to "old"))
        val updated = original.withDeclaredDeps(mapOf("n2" to "new"))
        assertNull(updated.declaredDeps["n1"])
        assertEquals("new", updated.declaredDeps["n2"])
    }

    // ─── Schema 感知数据访问 ─────────────────────────────────────

    @Test
    fun `inputField falls back to Map access when no provider`() {
        val ni = input(directInput = mapOf("name" to "Alice", "age" to 30))
        assertEquals("Alice", ni.inputField("name"))
        assertEquals(30, ni.inputField("age"))
        assertNull(ni.inputField("missing"))
    }

    @Test
    fun `inputField uses provider when schemaFormat is set`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val ni = NodeInput(
            directInput = mapOf("id" to 1, "name" to "Bob"),
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        assertEquals(1, ni.inputField("id"))
        assertEquals("Bob", ni.inputField("name"))
    }

    @Test
    fun `requireInputField throws on missing`() {
        val ni = input(directInput = emptyMap<String, Any>())
        assertThrows<InvalidParamException> { ni.requireInputField("missing") }
    }

    @Test
    fun `inputFieldOrDefault returns default when missing`() {
        val ni = input(directInput = mapOf("a" to 1))
        assertEquals(1, ni.inputFieldOrDefault("a", 99))
        assertEquals(99, ni.inputFieldOrDefault("b", 99))
    }

    @Test
    fun `hasInputField checks existence`() {
        val ni = input(directInput = mapOf("a" to 1))
        assertTrue(ni.hasInputField("a"))
        assertFalse(ni.hasInputField("b"))
        assertFalse(input(directInput = null).hasInputField("a"))
    }

    @Test
    fun `inputAsMap converts to map`() {
        val data = mapOf("x" to 1, "y" to "hello")
        val ni = input(directInput = data)
        assertEquals(data, ni.inputAsMap())
        assertEquals(emptyMap<String, Any?>(), input(directInput = null).inputAsMap())
    }

    @Test
    fun `inputFieldNames returns all keys`() {
        val ni = input(directInput = mapOf("a" to 1, "b" to 2))
        assertEquals(setOf("a", "b"), ni.inputFieldNames())
        assertEquals(emptySet<String>(), input(directInput = null).inputFieldNames())
    }

    @Test
    fun `resolvedSchemaFormat returns null when not set`() {
        val ni = input()
        assertNull(ni.resolvedSchemaFormat)
    }

    @Test
    fun `resolvedSchemaFormat parses format code`() {
        val ni = input().copy(schemaFormat = "protobuf")
        assertEquals(com.fluxion.schema.model.SchemaFormat.PROTOBUF, ni.resolvedSchemaFormat)
    }

    @Test
    fun `depField falls back to Map when no provider`() {
        val ni = input(declaredDeps = mapOf("n1" to mapOf("result" to "ok")))
        assertEquals("ok", ni.depField("n1", "result"))
        assertNull(ni.depField("n1", "missing"))
        assertNull(ni.depField("n2", "result"))
    }

    @Test
    fun `requireDepField throws on missing`() {
        val ni = input(declaredDeps = mapOf("n1" to mapOf("a" to 1)))
        assertThrows<InvalidParamException> { ni.requireDepField("n1", "b") }
        assertThrows<InvalidParamException> { ni.requireDepField("n2", "a") }
    }

    @Test
    fun `withSchema sets schema metadata`() {
        val ni = input().withSchema("protobuf", "UserMessage", 2L)
        assertEquals("protobuf", ni.schemaFormat)
        assertEquals("UserMessage", ni.schemaName)
        assertEquals(2L, ni.schemaVersion)
    }

    @Test
    fun `withProviderRegistry sets registry`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val ni = input().withProviderRegistry(registry)
        assertSame(registry, ni.providerRegistry)
    }

    // ─── directInputView / depView (SchemaBackedMap) ─────────────

    @Test
    fun `directInputView returns empty map when directInput is null`() {
        val ni = input(directInput = null)
        assertTrue(ni.directInputView.isEmpty())
    }

    @Test
    fun `directInputView returns standard Map when no schema configured`() {
        val data = mapOf("name" to "Alice", "age" to 30)
        val ni = input(directInput = data)
        assertFalse(ni.directInputView is SchemaBackedMap)
        assertEquals("Alice", ni.directInputView["name"])
        assertEquals(30, ni.directInputView["age"])
    }

    @Test
    fun `directInputView returns SchemaBackedMap when schema configured`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val data = mapOf("id" to 1, "name" to "Bob")
        val ni = NodeInput(
            directInput = data,
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        assertTrue(ni.directInputView is SchemaBackedMap)
        assertEquals(1, ni.directInputView["id"])
        assertEquals("Bob", ni.directInputView["name"])
        assertTrue(ni.directInputView.containsKey("id"))
        assertFalse(ni.directInputView.containsKey("missing"))
    }

    @Test
    fun `directInputView supports forEach iteration`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val data = mapOf("a" to 1, "b" to 2)
        val ni = NodeInput(
            directInput = data,
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        val collected = mutableMapOf<String, Any?>()
        ni.directInputView.forEach { (k, v) -> collected[k] = v }
        assertEquals(mapOf("a" to 1, "b" to 2), collected)
    }

    @Test
    fun `depView returns empty map for missing nodeId`() {
        val ni = input(declaredDeps = emptyMap())
        assertTrue(ni.depView("n1").isEmpty())
    }

    @Test
    fun `depView returns standard Map when no schema configured`() {
        val ni = input(declaredDeps = mapOf("n1" to mapOf("result" to "ok")))
        val view = ni.depView("n1")
        assertFalse(view is SchemaBackedMap)
        assertEquals("ok", view["result"])
    }

    @Test
    fun `depView returns SchemaBackedMap when schema configured`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val ni = NodeInput(
            directInput = null,
            declaredDeps = mapOf("n1" to mapOf("count" to 42, "status" to "done")),
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        val view = ni.depView("n1")
        assertTrue(view is SchemaBackedMap)
        assertEquals(42, view["count"])
        assertEquals("done", view["status"])
        assertTrue(view.containsKey("count"))
    }

    @Test
    fun `lookupVariable uses directInputView for schema-aware access`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val ni = NodeInput(
            directInput = mapOf("city" to "Shanghai"),
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        // resolveBinding accesses directInput via directInputView
        assertEquals("city=Shanghai", ni.resolveBinding("city=\${city}"))
    }

    @Test
    fun `resolveBinding uses depView for schema-aware dep field access`() {
        val registry = SchemaDataProviderRegistry(mapOf(com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA to JsonSchemaDataProvider()))
        val ni = NodeInput(
            directInput = null,
            declaredDeps = mapOf("n2" to mapOf("token" to "abc-123")),
            schemaFormat = "json-schema",
            providerRegistry = registry
        )
        // $ref with nodeId.field pattern
        val binding = mapOf("\$ref" to "n2.token")
        assertEquals("abc-123", ni.resolveBinding(binding))
    }
}
