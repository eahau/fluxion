package com.fluxion.script.meta

import com.fasterxml.jackson.core.type.TypeReference
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Contract test — validates that every `FunctionMeta` published by the
 * script-engine module conforms to the project's schema conventions.
 *
 * Guarantees enforced (per test case):
 * - meta.name exactly matches the declared `functionRef`.
 * - description is populated (not null / blank).
 * - `inputSchema` (if present) is a syntactically valid JSON Schema v7 document.
 * - Each declared required + optional parameter has a matching `properties`
 *   key in the schema; required parameters must also appear in the
 *   schema's `required` list.
 *
 * Uses JUnit 5 `@TestFactory` so adding a new built-in to
 * `ScriptEngineFunctionMetas` automatically gains a contract test without
 * editing this file.
 */
class ScriptEngineMetaContractTest {

    data class ContractCase(
        val functionRef: String,
        val optionalParams: List<String> = emptyList()
    )

    private val cases = listOf(
        ContractCase("builtin:groovyScript", optionalParams = listOf("script", "scriptRef"))
    )

    /** Dynamic tests — one per function-ref declared in [cases]. */
    @TestFactory
    fun `script engine function meta contract`(): List<DynamicTest> = cases.map { case ->
        DynamicTest.dynamicTest("${case.functionRef} meta contract") {
            val meta = metaByRef(case.functionRef)

            assertEquals(case.functionRef, meta.name)
            assertFalse(meta.description.isNullOrBlank())
            assertSchemaValid(meta.inputSchema, "inputSchema")
            assertParamCoverage(meta.inputSchema, emptyList(), case.optionalParams)
        }
    }

    /**
     * Reflectively walks the static [FunctionMeta] fields on
     * `ScriptEngineFunctionMetas` and returns the one whose name matches.
     */
    private fun metaByRef(ref: String): FunctionMeta {
        return ScriptEngineFunctionMetas::class.java.declaredFields
            .filter { it.type == FunctionMeta::class.java }
            .map { it.get(null) as FunctionMeta }
            .first { it.name == ref }
    }

    /**
     * Parse the schema (string or object form) through the networknt JSON
     * Schema validator; failure means the schema is malformed.
     */
    private fun assertSchemaValid(schema: Any?, label: String) {
        if (schema == null) return
        val schemaJson = if (schema is String) schema else JsonUtil.serialize(schema)
        try {
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaJson)
        } catch (ex: Exception) {
            fail<Any>("$label is not a valid JSON Schema: `$schemaJson", ex)
        }
    }

    /**
     * Cross-check that every expected parameter (required or optional) is
     * declared in the schema's `properties` map, and that every required
     * param is additionally listed in the schema's `required` array.
     */
    private fun assertParamCoverage(schema: Any?, required: List<String>, optional: List<String>) {
        if (schema == null) {
            assertTrue(required.isEmpty() && optional.isEmpty())
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
