package com.fluxion.builtin.meta

import com.fasterxml.jackson.core.type.TypeReference
import com.fluxion.schema.json.JsonSchemaParser
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * 内置函数元信息契约测试。
 *
 * 验证每个 builtin 函数的元信息常量：
 * 1. 名称、描述非空；
 * 2. input/output schema 是合法 JSON（若有）；
 * 3. input schema 至少覆盖实现代码中实际读取的 nodeParams 字段。
 */
class BuiltinFunctionMetaContractTest {

    data class ContractCase(
        val functionRef: String,
        val requiredParams: List<String> = emptyList(),
        val optionalParams: List<String> = emptyList()
    )

    private val cases = listOf(
        ContractCase("builtin:httpCall", requiredParams = listOf("url"), optionalParams = listOf("method", "headers", "bodyTemplate", "timeout", "responseType")),
        ContractCase("builtin:toJson", optionalParams = listOf("pretty", "nullValues")),
        ContractCase("builtin:fromJson", optionalParams = listOf("json", "type")),
        ContractCase("builtin:convert", requiredParams = listOf("targetType"), optionalParams = listOf("targetClass", "source", "pretty")),
        ContractCase("builtin:jsonExtract", requiredParams = listOf("mappings"), optionalParams = listOf("defaultOnMissing")),
        ContractCase("builtin:jsonTransform", requiredParams = listOf("expression"), optionalParams = listOf("defaultValue")),
        ContractCase("builtin:dbExecute", requiredParams = listOf("sql"), optionalParams = listOf("dataSource", "resultType", "limit", "compensateSql", "compensateFunctionRef")),
        ContractCase("builtin:mqPublish", requiredParams = listOf("topic"), optionalParams = listOf("key", "value", "async")),
        ContractCase("builtin:paramValidate", requiredParams = listOf("schema")),
        ContractCase("builtin:responseWrapper", optionalParams = listOf("code", "message", "data")),
        ContractCase("builtin:errorWrapper", optionalParams = listOf("code", "message", "data")),
        ContractCase("builtin:cacheGet", requiredParams = listOf("key")),
        ContractCase("builtin:cacheSet", requiredParams = listOf("key"), optionalParams = listOf("ttlSeconds")),
        ContractCase("builtin:conditionBranch", requiredParams = listOf("conditions"), optionalParams = listOf("defaultTarget")),
        ContractCase("builtin:filter", optionalParams = listOf("logic", "rules", "negate", "condition")),
        ContractCase("builtin:loopAggregator", optionalParams = listOf("mode")),
        ContractCase("builtin:paginate", requiredParams = listOf("total"), optionalParams = listOf("page", "size"))
    )

    @TestFactory
    fun `builtin function meta contract`(): List<DynamicTest> = cases.map { case ->
        DynamicTest.dynamicTest("${case.functionRef} meta contract") {
            val meta = metaByRef(case.functionRef)

            assertEquals(case.functionRef, meta.name, "function name must match resource")
            assertFalse(meta.description.isNullOrBlank(), "description should not be empty")

            assertSchemaValid(meta.inputSchema, "inputSchema")
            assertSchemaValid(meta.outputSchema, "outputSchema")
            assertSchemaComplete(meta.inputSchema, "inputSchema")
            assertSchemaComplete(meta.outputSchema, "outputSchema")

            assertParamCoverage(meta.inputSchema, case.requiredParams, case.optionalParams)
        }
    }

    private fun metaByRef(ref: String): FunctionMeta {
        return BuiltinFunctionMetas::class.java.declaredFields
            .filter { it.type == FunctionMeta::class.java }
            .map { it.get(null) as FunctionMeta }
            .first { it.name == ref }
    }

    private val schemaParser = JsonSchemaParser()

    private fun assertSchemaValid(schema: Any?, label: String) {
        if (schema == null) return
        val schemaJson = if (schema is String) schema else JsonUtil.serialize(schema)
        try {
            schemaParser.parse(null, schemaJson)
        } catch (ex: Exception) {
            fail("$label is not a valid JSON Schema: $schemaJson", ex)
        }
    }

    private fun assertSchemaComplete(schema: Any?, label: String) {
        if (schema == null) return
        val schemaJson = if (schema is String) schema else JsonUtil.serialize(schema)
        val schemaMap = JsonUtil.deserialize(schemaJson, object : TypeReference<Map<String, Any>>() {})
        val properties = schemaMap["properties"].uncheckedCast<Map<String, Any>>()
        val requiredList = (schemaMap["required"] as? List<*>)?.map { it.toString() } ?: emptyList()

        // 定义了 properties 的 schema 必须声明 type=object，保证前端表单与校验器行为一致
        if (properties != null && properties.isNotEmpty()) {
            assertEquals("object", schemaMap["type"], "$label with properties must declare type=object")
        }
        // required 中的字段必须出现在 properties 中
        for (param in requiredList) {
            assertTrue(properties?.containsKey(param) == true, "$label required field '$param' must be defined in properties")
        }
    }

    private fun assertParamCoverage(schema: Any?, required: List<String>, optional: List<String>) {
        if (schema == null) {
            assertTrue(required.isEmpty() && optional.isEmpty(), "schema is null but params were expected")
            return
        }
        val schemaJson = if (schema is String) schema else JsonUtil.serialize(schema)
        val schemaMap = JsonUtil.deserialize(schemaJson, object : TypeReference<Map<String, Any>>() {})
        val properties = schemaMap["properties"].uncheckedCast<Map<String, Any>>() ?: emptyMap()
        val requiredList = (schemaMap["required"] as? List<*>)?.map { it.toString() } ?: emptyList()

        for (param in required + optional) {
            assertTrue(properties.containsKey(param), "schema should define property '$param'")
        }
        for (param in required) {
            assertTrue(requiredList.contains(param), "schema should mark '$param' as required")
        }
    }
}
