package com.fluxion.admin.controller

import com.fluxion.admin.generated.model.TestFunctionRequest
import com.fluxion.admin.mapper.FunctionMapper
import com.fluxion.admin.mapper.JsonMapperHelper
import com.fluxion.admin.repository.WfFunctionRepository
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.service.WfFunctionService
import com.fluxion.core.function.FunctionRegistry
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.json.JsonSchemaFieldExtractor
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.schema.json.JsonSchemaValidator
import com.fluxion.schema.DefaultSchemaManager
import com.fluxion.core.value.FunctionMeta
import com.fluxion.core.value.FunctionResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.Optional

/**
 * 函数测试接口 Controller 单元测试。
 *
 * 覆盖：BUILTIN 函数成功执行、出参 schema 校验失败、函数未找到、执行异常信息结构化、
 * directInput/workflowInput/declaredDeps 透传。
 */
class WfFunctionControllerTest {

    private val functionRegistry = FunctionRegistry()
    private val functionRepository = mock(WfFunctionRepository::class.java)
    private val functionMapper = mock(FunctionMapper::class.java)
    private val functionService = mock(WfFunctionService::class.java)
    private val securityContext = mock(SecurityContextHelper::class.java)
    private val jsonMapperHelper = JsonMapperHelper()
    private val schemaManager: SchemaManager = DefaultSchemaManager(
        bundles = listOf(
            com.fluxion.schema.api.SchemaFormatBundle(
                format = com.fluxion.schema.model.SchemaFormat.JSON_SCHEMA,
                parser = JsonSchemaParser(),
                validator = JsonSchemaValidator(),
                extractor = JsonSchemaFieldExtractor()
            )
        )
    )

    private val controller = WfFunctionController(
        functionRegistry = functionRegistry,
        functionRepository = functionRepository,
        functionMapper = functionMapper,
        functionService = functionService,
        securityContext = securityContext,
        jsonMapperHelper = jsonMapperHelper,
        groovyScriptFunction = null,
        schemaManager = schemaManager
    )

    @Test
    fun `test builtin function success and output validation`() {
        val outputSchema = mapOf(
            "type" to "object",
            "properties" to mapOf("id" to mapOf("type" to "integer"))
        )
        functionRegistry.register(
            "builtin:mockSuccess",
            FunctionMeta.builder("builtin:mockSuccess")
                .outputSchema(outputSchema)
                .build(),
            WorkflowFunction { FunctionResult.success(mapOf("id" to 42)) }
        )

        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf()
        }
        val response = controller.testFunction("builtin:mockSuccess", request)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertTrue(body.success == true)
        assertEquals(mapOf("id" to 42), body.output)
        assertTrue(body.outputValid == true)
        assertTrue(body.validationErrors.isNullOrEmpty())
        assertNotNull(body.logs)
        assertTrue(body.logs!!.any { it.contains("success") })
    }

    @Test
    fun `test builtin function output schema validation failure`() {
        val outputSchema = mapOf(
            "type" to "object",
            "properties" to mapOf("id" to mapOf("type" to "integer")),
            "required" to listOf("id")
        )
        functionRegistry.register(
            "builtin:mockBadOutput",
            FunctionMeta.builder("builtin:mockBadOutput")
                .outputSchema(outputSchema)
                .build(),
            WorkflowFunction { FunctionResult.success(mapOf("id" to "not-an-integer")) }
        )

        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf()
        }
        val response = controller.testFunction("builtin:mockBadOutput", request)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertTrue(body.success == true)
        assertFalse(body.outputValid == true)
        assertFalse(body.validationErrors.isNullOrEmpty())
    }

    @Test
    fun `test function not found`() {
        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf("x" to 1)
        }
        val response = controller.testFunction("builtin:notExists", request)

        assertEquals(400, response.statusCode.value())
        val body = response.body!!
        assertFalse(body.success == true)
        assertTrue(body.error?.contains("Function not found") == true)
    }

    @Test
    fun `test execution exception includes errorType and errorStack`() {
        functionRegistry.register(
            "builtin:mockError",
            FunctionMeta.of("builtin:mockError"),
            object : WorkflowFunction<Any?> {
                override fun apply(input: NodeInput): FunctionResult<Any?> { throw IllegalStateException("boom") }
                override fun meta(): FunctionMeta = FunctionMeta.of("builtin:mockError")
            }
        )

        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf()
        }
        val response = controller.testFunction("builtin:mockError", request)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertFalse(body.success == true)
        assertEquals("boom", body.error)
        assertEquals("IllegalStateException", body.errorType)
        assertNotNull(body.errorStack)
        assertTrue(body.errorStack?.contains("IllegalStateException") == true)
        assertTrue(body.errorStack?.contains("at ") == true)
    }

    @Test
    fun `test directInput workflowInput and declaredDeps are passed to function`() {
        var capturedInput: NodeInput? = null
        functionRegistry.register(
            "builtin:mockCapture",
            FunctionMeta.of("builtin:mockCapture"),
            WorkflowFunction { input ->
                capturedInput = input
                FunctionResult.success(null)
            }
        )

        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf("nodeParam" to 1)
            directInput = mutableMapOf("direct" to 2)
            workflowInput = mutableMapOf("wf" to 3)
            declaredDeps = mutableMapOf("n1" to mapOf("dep" to 4))
        }
        controller.testFunction("builtin:mockCapture", request)

        assertNotNull(capturedInput)
        assertEquals(mapOf("nodeParam" to 1), capturedInput!!.nodeParams)
        assertEquals(mapOf("direct" to 2), capturedInput!!.directInput)
        assertEquals(mapOf("wf" to 3), capturedInput!!.workflowInput)
        assertEquals(mapOf("n1" to mapOf("dep" to 4)), capturedInput!!.declaredDeps)
    }

    @Test
    fun `test directInput defaults to inputs when not provided`() {
        var capturedInput: NodeInput? = null
        functionRegistry.register(
            "builtin:mockDefaultDirectInput",
            FunctionMeta.of("builtin:mockDefaultDirectInput"),
            WorkflowFunction { input ->
                capturedInput = input
                FunctionResult.success(null)
            }
        )

        val request = TestFunctionRequest().apply {
            inputs = mutableMapOf("key" to "value")
        }
        controller.testFunction("builtin:mockDefaultDirectInput", request)

        assertNotNull(capturedInput)
        assertEquals(mapOf("key" to "value"), capturedInput!!.directInput)
        assertEquals(mapOf("key" to "value"), capturedInput!!.nodeParams)
    }
}
