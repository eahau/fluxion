package com.fluxion.admin.service

import com.fluxion.admin.entity.WfDefinition
import com.fluxion.admin.entity.WfFunction
import com.fluxion.admin.generated.model.FunctionStatus
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfDefinitionRepository
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * WfDefinitionService 发布阶段预编译测试。
 */
class WfDefinitionServiceCompileTest {

    private val functionRegistry = FunctionRegistry().apply {
        register(
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
            object : WorkflowFunction<Any?> {
                override fun apply(input: NodeInput): FunctionResult<Any?> = FunctionResult.success(null)
                override fun meta(): FunctionMeta = FunctionMeta.of("mock:getUser")
            }
        )
    }

    private fun activeWfFunction() = WfFunction().apply { status = FunctionStatus.ACTIVE }

    @Test
    fun `publish compiles dbExecute nodes using upstream schema`() {
        val definitionRepository = mock(WfDefinitionRepository::class.java)
        val functionRepository = mock(WfFunctionRepository::class.java)
        val service = WfDefinitionService(
            repository = definitionRepository,
            functionRepository = functionRepository,
            functionRegistry = functionRegistry,
            configPublisher = null,
            jsonMapperHelper = JsonMapperHelper(),
            eventPublisher = null
        )

        val dagJson = JsonUtil.serialize(
            mapOf(
                "workflowId" to "wf1",
                "extraField" to "preserved",
                "nodes" to listOf(
                    mapOf(
                        "id" to "n1",
                        "functionRef" to "mock:getUser"
                    ),
                    mapOf(
                        "id" to "n2",
                        "functionRef" to "builtin:dbExecute",
                        "params" to mapOf(
                            "sql" to "SELECT * FROM users WHERE id = :id AND name = :name",
                            "dataSource" to "default"
                        )
                    )
                )
            )
        )

        val entity = WfDefinition().apply {
            workflowId = "wf1"
            workflowName = "test"
            status = "DRAFT"
            version = 1
            this.dagJson = dagJson
        }

        `when`(definitionRepository.findByWorkflowId("wf1")).thenReturn(Optional.of(entity))
        `when`(definitionRepository.save(entity)).thenReturn(entity)
        `when`(functionRepository.findByFunctionName("mock:getUser")).thenReturn(Optional.of(activeWfFunction()))
        `when`(functionRepository.findByFunctionName("builtin:dbExecute")).thenReturn(Optional.of(activeWfFunction()))

        service.publish("wf1")

        val publishedDag = JsonUtil.toMap(entity.dagJson)
        assertEquals("preserved", publishedDag["extraField"])
        val nodes = JsonUtil.convertValueOrNull<List<Map<String, Any>>>(publishedDag["nodes"]) ?: emptyList()
        val dbNode = nodes.first { it["functionRef"] == "builtin:dbExecute" }
        val params = JsonUtil.convertValueOrNull<Map<String, Any>>(dbNode["params"]) ?: emptyMap()
        val compiledSql = JsonUtil.convertValueOrNull<Map<String, Any>>(params["compiledSql"]) ?: emptyMap()
        assertNotNull(compiledSql["sql"])
        val sqlCompiled = JsonUtil.convertValueOrNull<Map<String, Any>>(compiledSql["sql"]) ?: emptyMap()
        assertEquals(listOf("id", "name"), sqlCompiled["paramNames"])
    }
}
