package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

interface RestApiStrategy {
    fun buildRegisterPath(subjectName: String): String
    fun buildGetPath(subjectName: String, version: Long?): String
    fun buildListPath(): String
    fun buildListVersionsPath(subjectName: String): String
    fun buildDeletePath(subjectName: String): String
    fun buildUpdatePath(subjectName: String): String

    fun buildRegisterPayload(name: String, format: SchemaFormat, raw: String): String
    fun buildUpdatePayload(raw: String): String

    fun getAcceptHeader(): String
    fun getContentTypeHeader(): String

    fun extractSchemaFromResponse(response: String): String
    fun extractIdFromResponse(response: String): String
    fun extractNameFromIdentifier(identifier: String): String
}
