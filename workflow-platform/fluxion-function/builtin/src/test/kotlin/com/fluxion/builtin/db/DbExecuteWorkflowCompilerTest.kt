package com.fluxion.builtin.db

import com.fluxion.builtin.db.sql.CompiledSql
import com.fluxion.builtin.db.workflow.DbExecuteWorkflowCompiler
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [DbExecuteWorkflowCompiler].
 */
class DbExecuteWorkflowCompilerTest {

    private val dummyFunction = object : WorkflowFunction<Any?> {
        override fun apply(input: NodeInput): FunctionResult<Any?> = FunctionResult.success(null)
        override fun meta(): FunctionMeta = FunctionMeta.of("dummy")
    }

    private fun buildRegistry(): FunctionRegistry {
        val registry = FunctionRegistry()
        registry.register(
            "mock:getUser",
            FunctionMeta.builder("mock:getUser")
                .outputSchema(
                    mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "integer"),
                            "name" to mapOf("type" to "string")
                        )
                    )
                )
                .build(),
            dummyFunction
        )
        return registry
    }

    @Test
    fun `compile linear workflow using previous node output schema`() {
        val registry = buildRegistry()
        val definition = WorkflowDefinition(
            nodes = listOf(
                WorkflowNode(id = "n1", functionRef = "mock:getUser"),
                WorkflowNode(
                    id = "n2",
                    functionRef = "builtin:dbExecute",
                    params = mapOf(
                        "sql" to "SELECT * FROM \${table} WHERE id = :id AND name = :name",
                        "table" to "users"
                    )
                )
            )
        )

        val compiled = DbExecuteWorkflowCompiler.compile(definition, registry)
        val n2 = compiled.nodes[1]
        assertNotNull(n2.params?.get("compiledSql"))
        val compiledSql = n2.params?.get("compiledSql").uncheckedCast<Map<String, CompiledSql>>()!!
        assertTrue(compiledSql.containsKey("sql"))
        assertEquals(listOf("id", "name"), compiledSql["sql"]?.paramNames)
    }

    @Test
    fun `compile dag workflow using dependsOn output schema`() {
        val registry = buildRegistry()
        val definition = WorkflowDefinition(
            nodes = listOf(
                WorkflowNode(id = "n1", functionRef = "mock:getUser"),
                WorkflowNode(id = "n2", functionRef = "builtin:common"),
                WorkflowNode(
                    id = "n3",
                    functionRef = "builtin:dbExecute",
                    dependsOn = listOf("n1"),
                    params = mapOf("sql" to "SELECT * FROM users WHERE id = :id")
                )
            )
        )

        val compiled = DbExecuteWorkflowCompiler.compile(definition, registry)
        val n3 = compiled.nodes[2]
        val compiledSql = n3.params?.get("compiledSql").uncheckedCast<Map<String, CompiledSql>>()!!
        assertEquals(listOf("id"), compiledSql["sql"]?.paramNames)
    }

    @Test
    fun `compile fails when param not found`() {
        val registry = buildRegistry()
        val definition = WorkflowDefinition(
            nodes = listOf(
                WorkflowNode(id = "n1", functionRef = "mock:getUser"),
                WorkflowNode(
                    id = "n2",
                    functionRef = "builtin:dbExecute",
                    params = mapOf("sql" to "SELECT * WHERE unknown = :unknown")
                )
            )
        )

        assertThrows<IllegalArgumentException> {
            DbExecuteWorkflowCompiler.compile(definition, registry)
        }
    }

    @Test
    fun `compile fails when variable type is not string`() {
        val registry = buildRegistry()
        val definition = WorkflowDefinition(
            nodes = listOf(
                WorkflowNode(id = "n1", functionRef = "mock:getUser"),
                WorkflowNode(
                    id = "n2",
                    functionRef = "builtin:dbExecute",
                    params = mapOf("sql" to "SELECT * FROM \${id} WHERE name = :name")
                )
            )
        )

        assertThrows<IllegalArgumentException> {
            DbExecuteWorkflowCompiler.compile(definition, registry)
        }
    }

    @Test
    fun `compiled definition can be serialized and deserialized`() {
        val registry = buildRegistry()
        val definition = WorkflowDefinition(
            nodes = listOf(
                WorkflowNode(id = "n1", functionRef = "mock:getUser"),
                WorkflowNode(
                    id = "n2",
                    functionRef = "builtin:dbExecute",
                    params = mapOf(
                        "sql" to "SELECT * FROM users WHERE id = :id",
                        "dataSource" to "default"
                    )
                )
            )
        )

        val compiled = DbExecuteWorkflowCompiler.compile(definition, registry)
        val json = JsonUtil.serialize(compiled)
        val parsed = JsonUtil.deserialize(json, WorkflowDefinition::class.java)

        val n2 = parsed.nodes[1]
        val compiledSql = n2.params?.get("compiledSql").uncheckedCast<Map<String, Any>>()!!
        val sqlCompiled = compiledSql["sql"].uncheckedCast<Map<String, Any>>()!!
        assertEquals("SELECT * FROM users WHERE id = :id", sqlCompiled["originalSql"])
        assertEquals(listOf("id"), sqlCompiled["paramNames"])
    }
}
