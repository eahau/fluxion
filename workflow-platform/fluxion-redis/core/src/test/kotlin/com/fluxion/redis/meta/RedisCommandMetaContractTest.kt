package com.fluxion.redis.meta

import com.fasterxml.jackson.core.type.TypeReference
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.fluxion.core.util.JsonUtil
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RedisCommandMetaContractTest {

    @Test
    fun `redisCommand meta contract`() {
        val meta = RedisFunctionMetas.REDIS_COMMAND

        assertEquals("builtin:redisCommand", meta.name)
        assertFalse(meta.description.isNullOrBlank())
        assertSchemaValid(meta.inputSchema)
        assertParamCoverage(
            meta.inputSchema,
            required = emptyList(),
            optional = listOf("command", "key", "args", "raw", "script", "keys", "commands")
        )
    }

    private fun assertSchemaValid(schema: Any?) {
        if (schema == null) return
        val schemaJson = schema as? String ?: JsonUtil.serialize(schema)
        try {
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaJson)
        } catch (ex: Exception) {
            fail("inputSchema is not a valid JSON Schema: $schemaJson", ex)
        }
    }

    private fun assertParamCoverage(schema: Any?, required: List<String>, optional: List<String>) {
        val schemaJson = schema as? String ?: JsonUtil.serialize(schema)
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
