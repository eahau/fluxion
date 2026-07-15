package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

object ConfluentApiStrategy : RestApiStrategy {

    override fun buildRegisterPath(subjectName: String): String {
        return "/subjects/$subjectName/versions"
    }

    override fun buildGetPath(subjectName: String, version: Long?): String {
        return if (version != null) {
            "/subjects/$subjectName/versions/$version"
        } else {
            "/subjects/$subjectName/versions/latest"
        }
    }

    override fun buildListPath(): String {
        return "/subjects"
    }

    override fun buildListVersionsPath(subjectName: String): String {
        return "/subjects/$subjectName/versions"
    }

    override fun buildDeletePath(subjectName: String): String {
        return "/subjects/$subjectName"
    }

    override fun buildUpdatePath(subjectName: String): String {
        return "/subjects/$subjectName/versions"
    }

    override fun buildRegisterPayload(name: String, format: SchemaFormat, raw: String): String {
        val schemaType = mapFormat(format)
        return """{"schema": "${escapeJson(raw)}", "schemaType": "$schemaType"}"""
    }

    override fun buildUpdatePayload(raw: String): String {
        return """{"schema": "${escapeJson(raw)}"}"""
    }

    override fun getAcceptHeader(): String {
        return "application/vnd.schemaregistry.v1+json"
    }

    override fun getContentTypeHeader(): String {
        return "application/vnd.schemaregistry.v1+json"
    }

    override fun extractSchemaFromResponse(response: String): String {
        val match = """"schema"\s*:\s*"([^"]+)""".toRegex().find(response)
        return match?.groupValues?.get(1)?.replace("\\\"", "\"") ?: response
    }

    override fun extractIdFromResponse(response: String): String {
        val match = """"id"\s*:\s*(\d+)""".toRegex().find(response)
        return match?.groupValues?.get(1) ?: "0"
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
            SchemaFormat.JSON_SCHEMA -> "JSON"
            SchemaFormat.AVRO -> "AVRO"
            SchemaFormat.PROTOBUF -> "PROTOBUF"
            else -> "JSON"
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
