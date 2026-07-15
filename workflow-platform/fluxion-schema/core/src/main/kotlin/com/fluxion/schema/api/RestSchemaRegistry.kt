package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import org.slf4j.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

abstract class RestSchemaRegistry(
    private val url: String,
    private val username: String? = null,
    private val password: String? = null,
    private val apiStrategy: RestApiStrategy
) : ExternalSchemaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun register(name: String, format: SchemaFormat, raw: String): Schema {
        return register(name, format, raw, 0)
    }

    override fun register(name: String, format: SchemaFormat, raw: String, version: Long): Schema {
        val subjectName = getSubjectName(name, format)

        try {
            val path = apiStrategy.buildRegisterPath(subjectName)
            val payload = apiStrategy.buildRegisterPayload(name, format, raw)
            val response = postRequest(path, payload)

            val id = apiStrategy.extractIdFromResponse(response)
            log.info { "Registered schema: name=$name, subject=$subjectName, id=$id" }

            return get(name, null) ?: throw RuntimeException("Failed to fetch registered schema")
        } catch (e: IOException) {
            log.error(e) { "Failed to register schema [$name] due to IO error" }
            throw RuntimeException("Failed to register schema [$name]", e)
        }
    }

    override fun update(name: String, format: SchemaFormat, raw: String): Schema {
        val subjectName = getSubjectName(name, format)

        try {
            val path = apiStrategy.buildUpdatePath(subjectName)
            val payload = apiStrategy.buildUpdatePayload(raw)
            putRequest(path, payload)

            log.info { "Updated schema: name=$name, subject=$subjectName" }
            return get(name, null) ?: throw RuntimeException("Failed to fetch updated schema")
        } catch (e: IOException) {
            log.error(e) { "Failed to update schema [$name] due to IO error" }
            throw RuntimeException("Failed to update schema [$name]", e)
        }
    }

    override fun delete(name: String): Boolean {
        val subjectName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)

        try {
            val path = apiStrategy.buildDeletePath(subjectName)
            val responseCode = deleteRequest(path)
            if (responseCode == 404) {
                log.debug { "Schema subject not found for deletion: $subjectName" }
                return false
            }
            log.info { "Deleted schema subject: $subjectName" }
            return true
        } catch (e: IOException) {
            log.error(e) { "Failed to delete schema [$name] due to IO error" }
            throw RuntimeException("Failed to delete schema [$name]", e)
        }
    }

    override fun get(name: String, version: Long?): Schema? {
        val subjectName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)

        return try {
            val path = apiStrategy.buildGetPath(subjectName, version)
            val response = getRequest(path)
            if (response.isEmpty()) {
                return null
            }

            val raw = apiStrategy.extractSchemaFromResponse(response)
            val format = inferFormatFromSchema(raw)

            Schema(
                name = name,
                format = format,
                raw = raw,
                parsed = raw
            )
        } catch (e: IOException) {
            log.error(e) { "Failed to fetch schema [$name] v$version due to IO error" }
            throw RuntimeException("Failed to fetch schema [$name] v$version", e)
        }
    }

    override fun list(): List<Schema> {
        return try {
            val path = apiStrategy.buildListPath()
            val response = getRequest(path)
            if (response.isEmpty()) {
                return emptyList()
            }

            val identifiers = parseJsonArray(response)
            identifiers.mapNotNull { identifier ->
                try {
                    get(apiStrategy.extractNameFromIdentifier(identifier), null)
                } catch (e: Exception) {
                    log.warn(e) { "Failed to fetch schema for identifier: $identifier" }
                    null
                }
            }
        } catch (e: IOException) {
            log.error(e) { "Failed to list schemas due to IO error" }
            throw RuntimeException("Failed to list schemas", e)
        }
    }

    override fun listVersions(name: String): List<Long> {
        val subjectName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)

        return try {
            val path = apiStrategy.buildListVersionsPath(subjectName)
            val response = getRequest(path)
            if (response.isEmpty()) {
                return emptyList()
            }

            parseJsonArray(response).map { it.toLong() }
        } catch (e: IOException) {
            log.error(e) { "Failed to list versions for schema [$name] due to IO error" }
            throw RuntimeException("Failed to list versions for schema [$name]", e)
        }
    }

    override fun getSubjectName(name: String, format: SchemaFormat): String {
        return "$name-${format.code}"
    }

    protected fun inferFormatFromSchema(raw: String): SchemaFormat {
        return when {
            raw.startsWith("{\"type\": \"record\"") -> SchemaFormat.AVRO
            raw.startsWith("syntax = \"proto") -> SchemaFormat.PROTOBUF
            else -> SchemaFormat.JSON_SCHEMA
        }
    }

    private fun getRequest(path: String): String {
        val url = URL("${this.url.removeSuffix("/")}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", apiStrategy.getAcceptHeader())
        setupAuth(conn)

        return try {
            val responseCode = conn.responseCode
            if (responseCode == 404) {
                ""
            } else if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw IOException("HTTP $responseCode: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun postRequest(path: String, payload: String): String {
        val url = URL("${this.url.removeSuffix("/")}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", apiStrategy.getContentTypeHeader())
        conn.setRequestProperty("Accept", apiStrategy.getAcceptHeader())
        conn.doOutput = true
        setupAuth(conn)

        return try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw IOException("HTTP $responseCode: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun putRequest(path: String, payload: String) {
        val url = URL("${this.url.removeSuffix("/")}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", apiStrategy.getContentTypeHeader())
        conn.setRequestProperty("Accept", apiStrategy.getAcceptHeader())
        conn.doOutput = true
        setupAuth(conn)

        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw IOException("HTTP $responseCode: $error")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun deleteRequest(path: String): Int {
        val url = URL("${this.url.removeSuffix("/")}$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Accept", apiStrategy.getAcceptHeader())
        setupAuth(conn)

        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun setupAuth(conn: HttpURLConnection) {
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val auth = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            conn.setRequestProperty("Authorization", "Basic $auth")
        }
    }

    protected fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun parseJsonArray(response: String): List<String> {
        val cleaned = response.trim().removePrefix("[").removeSuffix("]")
        if (cleaned.isEmpty()) {
            return emptyList()
        }
        return cleaned.split(",")
            .map { it.trim().removePrefix("\"").removeSuffix("\"") }
            .filter { it.isNotEmpty() }
    }
}
