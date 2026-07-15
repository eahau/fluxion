package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

object AzureApiStrategy : RestApiStrategy {

    override fun buildRegisterPath(subjectName: String): String {
        return "/schemas/register"
    }

    override fun buildGetPath(subjectName: String, version: Long?): String {
        return "/schemas/$subjectName"
    }

    override fun buildListPath(): String {
        return "/schemas"
    }

    override fun buildListVersionsPath(subjectName: String): String {
        return "/schemas/$subjectName/versions"
    }

    override fun buildDeletePath(subjectName: String): String {
        return "/schemas/$subjectName"
    }

    override fun buildUpdatePath(subjectName: String): String {
        return "/schemas/$subjectName"
    }

    override fun buildRegisterPayload(name: String, format: SchemaFormat, raw: String): String {
        val serializationType = mapFormat(format)
        return """{"schema": "${escapeJson(raw)}", "serializationType": "$serializationType"}"""
    }

    override fun buildUpdatePayload(raw: String): String {
        return """{"schema": "${escapeJson(raw)}"}"""
    }

    override fun getAcceptHeader(): String {
        return "application/json"
    }

    override fun getContentTypeHeader(): String {
        return "application/json"
    }

    override fun extractSchemaFromResponse(response: String): String {
        val match = """"schema"\s*:\s*"([^"]+)""".toRegex().find(response)
        return match?.groupValues?.get(1)?.replace("\\\"", "\"") ?: response
    }

    override fun extractIdFromResponse(response: String): String {
        val match = """"id"\s*:\s*"([^"]+)""".toRegex().find(response)
        return match?.groupValues?.get(1) ?: ""
    }

    override fun extractNameFromIdentifier(identifier: String): String {
        val lastDash = identifier.lastIndexOf('-')
        return if (lastDash > 0) {
            identifier.substring(0, lastDash)
        } else {
            identifier
        }
    }

    private fun mapFormat(format: SchemaFormat): String {
        return when (format) {
            SchemaFormat.AVRO -> "Avro"
            SchemaFormat.JSON_SCHEMA -> "Json"
            SchemaFormat.PROTOBUF -> "Protobuf"
            else -> "Json"
        }
    }

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
