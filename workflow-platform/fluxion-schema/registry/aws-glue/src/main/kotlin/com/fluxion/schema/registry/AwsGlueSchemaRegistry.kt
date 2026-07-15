package com.fluxion.schema.registry

import com.fluxion.schema.api.ExternalSchemaRegistry
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class AwsGlueSchemaRegistry(
    private val region: String,
    private val registryName: String = "default-registry"
) : ExternalSchemaRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val baseUrl = "https://glue.$region.amazonaws.com"

    override fun register(name: String, format: SchemaFormat, raw: String): Schema {
        val schemaName = getSubjectName(name, format)
        val dataFormat = mapFormat(format)

        try {
            val payload = """
                {
                    "SchemaId": {
                        "RegistryName": "$registryName",
                        "SchemaName": "$schemaName"
                    },
                    "SchemaDefinition": ${escapeJson(raw)}
                }
            """.trimIndent()

            val response = postRequest("/registerSchemaVersion", payload)
            val version = extractVersion(response)
            log.info("Registered schema '$schemaName' version $version in registry '$registryName'")
            return Schema(name, format, raw, raw)
        } catch (e: Exception) {
            val payload = """
                {
                    "RegistryId": {
                        "RegistryName": "$registryName"
                    },
                    "SchemaName": "$schemaName",
                    "DataFormat": "$dataFormat",
                    "Compatibility": "BACKWARD",
                    "SchemaDefinition": ${escapeJson(raw)}
                }
            """.trimIndent()

            val response = postRequest("/createSchema", payload)
            val version = extractVersion(response)
            log.info("Created and registered schema '$schemaName' version $version in registry '$registryName'")
            return Schema(name, format, raw, raw)
        }
    }

    override fun register(name: String, format: SchemaFormat, raw: String, version: Long): Schema {
        return register(name, format, raw)
    }

    override fun update(name: String, format: SchemaFormat, raw: String): Schema {
        return register(name, format, raw)
    }

    override fun delete(name: String): Boolean {
        val schemaName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)
        try {
            val payload = """
                {
                    "SchemaId": {
                        "RegistryName": "$registryName",
                        "SchemaName": "$schemaName"
                    }
                }
            """.trimIndent()

            postRequest("/deleteSchema", payload)
            log.info("Deleted schema '$schemaName' from registry '$registryName'")
            return true
        } catch (e: Exception) {
            log.warn("Failed to delete schema '$schemaName': ${e.message}")
            return false
        }
    }

    override fun get(name: String, version: Long?): Schema? {
        val schemaName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)
        try {
            val payload = """
                {
                    "SchemaId": {
                        "RegistryName": "$registryName",
                        "SchemaName": "$schemaName"
                    },
                    "SchemaVersionNumber": {
                        ${if (version != null) "\"VersionNumber\": $version" else "\"LatestVersion\": true"}
                    }
                }
            """.trimIndent()

            val response = postRequest("/getSchemaVersion", payload)
            val schemaDefinition = extractField(response, "SchemaDefinition")
            val dataFormat = extractField(response, "DataFormat")

            return Schema(
                name = name,
                format = mapDataFormat(dataFormat),
                raw = schemaDefinition ?: "",
                parsed = schemaDefinition ?: ""
            )
        } catch (e: Exception) {
            log.debug("Schema '$schemaName' not found in registry '$registryName'")
            return null
        }
    }

    override fun list(): List<Schema> {
        val schemas = mutableListOf<Schema>()
        var nextToken: String? = null

        do {
            val payload = """
                {
                    "RegistryId": {
                        "RegistryName": "$registryName"
                    }
                    ${nextToken?.let { ", \"NextToken\": \"$it\"" } ?: ""}
                }
            """.trimIndent()

            val response = postRequest("/listSchemas", payload)
            val schemasJson = extractField(response, "Schemas")
            if (schemasJson != null && schemasJson.startsWith("[")) {
                val schemaNames = extractArrayValues(schemasJson, "SchemaName")
                schemaNames.forEach { schemas.add(Schema(it, SchemaFormat.JSON_SCHEMA, "", "")) }
            }

            nextToken = extractField(response, "NextToken")
        } while (nextToken != null)

        log.debug("Found ${schemas.size} schemas in registry '$registryName'")
        return schemas
    }

    override fun listVersions(name: String): List<Long> {
        val schemaName = getSubjectName(name, SchemaFormat.JSON_SCHEMA)
        val versions = mutableListOf<Long>()
        var nextToken: String? = null

        try {
            do {
                val payload = """
                    {
                        "SchemaId": {
                            "RegistryName": "$registryName",
                            "SchemaName": "$schemaName"
                        }
                        ${nextToken?.let { ", \"NextToken\": \"$it\"" } ?: ""}
                    }
                """.trimIndent()

                val response = postRequest("/listSchemaVersions", payload)
                val versionsJson = extractField(response, "SchemaVersions")
                if (versionsJson != null && versionsJson.startsWith("[")) {
                    val versionNumbers = extractArrayValues(versionsJson, "VersionNumber")
                    versionNumbers.forEach { versions.add(it.toLong()) }
                }

                nextToken = extractField(response, "NextToken")
            } while (nextToken != null)

            log.debug("Found ${versions.size} versions for schema '$schemaName'")
        } catch (e: Exception) {
            log.debug("Schema '$schemaName' not found in registry '$registryName'")
        }

        return versions.sorted()
    }

    override fun getSubjectName(name: String, format: SchemaFormat): String {
        return "$name-${format.code}"
    }

    private fun mapFormat(format: SchemaFormat): String {
        return when (format) {
            SchemaFormat.AVRO -> "AVRO"
            SchemaFormat.JSON_SCHEMA -> "JSON"
            SchemaFormat.PROTOBUF -> "PROTOBUF"
            else -> "JSON"
        }
    }

    private fun mapDataFormat(dataFormat: String?): SchemaFormat {
        return when (dataFormat?.uppercase()) {
            "AVRO" -> SchemaFormat.AVRO
            "JSON" -> SchemaFormat.JSON_SCHEMA
            "PROTOBUF" -> SchemaFormat.PROTOBUF
            else -> SchemaFormat.JSON_SCHEMA
        }
    }

    private fun postRequest(path: String, payload: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl$path"))
            .header("Content-Type", "application/json")
            .header("X-Amz-Target", "AWSGlue.RegisterSchemaVersion")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }
        return response.body()
    }

    private fun escapeJson(str: String): String {
        return "\"${str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }

    private fun extractVersion(response: String): Long {
        return extractField(response, "VersionNumber")?.toLong() ?: 1L
    }

    private fun extractField(response: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*([^\",}]+)".toRegex()
        return pattern.find(response)?.groupValues?.get(1)?.trim('"')
    }

    private fun extractArrayValues(arrayJson: String, fieldName: String): List<String> {
        val values = mutableListOf<String>()
        val pattern = "\"$fieldName\"\\s*:\\s*([^\",}]+)".toRegex()
        pattern.findAll(arrayJson).forEach { match ->
            values.add(match.groupValues[1].trim('"'))
        }
        return values
    }
}
