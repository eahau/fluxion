package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

object ApicurioApiStrategy : RestApiStrategy {

    override fun buildRegisterPath(subjectName: String): String {
        return "/api/artifacts"
    }

    override fun buildGetPath(subjectName: String, version: Long?): String {
        return if (version != null && version > 0) {
            "/api/artifacts/$subjectName/versions/$version"
        } else {
            "/api/artifacts/$subjectName"
        }
    }

    override fun buildListPath(): String {
        return "/api/artifacts"
    }

    override fun buildListVersionsPath(subjectName: String): String {
        return "/api/artifacts/$subjectName/versions"
    }

    override fun buildDeletePath(subjectName: String): String {
        return "/api/artifacts/$subjectName"
    }

    override fun buildUpdatePath(subjectName: String): String {
        return "/api/artifacts/$subjectName"
    }

    override fun buildRegisterPayload(name: String, format: SchemaFormat, raw: String): String {
        return """{"name": "$name", "content": "${escapeJson(raw)}"}"""
    }

    override fun buildUpdatePayload(raw: String): String {
        return """{"content": "${escapeJson(raw)}"}"""
    }

    override fun getAcceptHeader(): String {
        return "application/json"
    }

    override fun getContentTypeHeader(): String {
        return "application/json"
    }

    override fun extractSchemaFromResponse(response: String): String {
        val match = """"content"\s*:\s*"([^"]+)""".toRegex().find(response)
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

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
