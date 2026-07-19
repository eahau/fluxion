package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.SchemasApi
import com.fluxion.admin.generated.model.*
import com.fluxion.admin.service.TableMetadataService
import com.fluxion.schema.api.ExternalSchemaRegistry
import com.fluxion.schema.api.SchemaManager
import com.fluxion.schema.model.SchemaFormat
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SchemasApiController(
    private val schemaManager: SchemaManager,
    private val schemaRegistry: ExternalSchemaRegistry,
    private val tableMetadataService: TableMetadataService
) : SchemasApi {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun listSchemas(
        keyword: String?,
        schemaType: String?,
        domain: String?,
        frozen: Boolean?,
        appGroup: String?,
        sortField: String,
        sortDirection: String,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseSchemaDefinition> {
        return try {
            val allSchemas = schemaRegistry.list()
                .filter { schema ->
                    val matchKeyword = keyword.isNullOrBlank() ||
                        schema.name?.lowercase()?.contains(keyword.lowercase()) == true
                    matchKeyword
                }

            val sorted = when (sortDirection.lowercase()) {
                "asc" -> allSchemas.sortedBy { it.name }
                else -> allSchemas.sortedByDescending { it.name }
            }

            val start = page * pageSize
            val end = (start + pageSize).coerceAtMost(sorted.size)
            val pageContent = if (start < sorted.size) sorted.subList(start, end) else emptyList()

            ResponseEntity.ok(PageResponseSchemaDefinition().apply {
                list = pageContent.map { toGeneratedSchema(it) }.toMutableList()
                total = allSchemas.size.toLong()
                this.page = page
                this.pageSize = pageSize
            })
        } catch (e: Exception) {
            log.error("Failed to list schemas", e)
            ResponseEntity.ok(PageResponseSchemaDefinition().apply {
                list = mutableListOf()
                total = 0L
                this.page = page
                this.pageSize = pageSize
            })
        }
    }

    override fun getSchema(schemaName: String): ResponseEntity<SchemaDefinition> {
        return try {
            val schema = schemaRegistry.get(schemaName, null)
                ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(toGeneratedSchema(schema))
        } catch (e: Exception) {
            log.error("Failed to get schema: $schemaName", e)
            ResponseEntity.notFound().build()
        }
    }

    override fun createSchema(schemaDefinition: SchemaDefinition): ResponseEntity<SchemaDefinition> {
        return try {
            val format = SchemaFormat.fromCodeOrNull(schemaDefinition.schemaFormat) ?: SchemaFormat.JSON_SCHEMA
            val created = schemaRegistry.register(
                name = schemaDefinition.schemaName,
                format = format,
                raw = schemaDefinition.schemaJson
            )
            ResponseEntity.status(HttpStatus.CREATED).body(toGeneratedSchema(created))
        } catch (e: Exception) {
            log.error("Failed to create schema: ${schemaDefinition.schemaName}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    override fun updateSchema(schemaName: String, schemaDefinition: SchemaDefinition): ResponseEntity<SchemaDefinition> {
        return try {
            val existing = schemaRegistry.get(schemaName, null)
                ?: return ResponseEntity.notFound().build()
            val format = SchemaFormat.fromCodeOrNull(schemaDefinition.schemaFormat) ?: existing.format
            val updated = schemaRegistry.update(schemaName, format, schemaDefinition.schemaJson)
            ResponseEntity.ok(toGeneratedSchema(updated))
        } catch (e: Exception) {
            log.error("Failed to update schema: $schemaName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    override fun deleteSchema(schemaName: String): ResponseEntity<Unit> {
        return try {
            val deleted = schemaRegistry.delete(schemaName)
            if (deleted) ResponseEntity.noContent().build()
            else ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error("Failed to delete schema: $schemaName", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    override fun listSchemaVersions(schemaName: String): ResponseEntity<List<SchemaDefinition>> {
        return try {
            val versions = schemaRegistry.listVersions(schemaName)
            val schemas = versions.mapNotNull { version ->
                schemaRegistry.get(schemaName, version)
            }
            ResponseEntity.ok(schemas.map { toGeneratedSchema(it) })
        } catch (e: Exception) {
            log.error("Failed to list schema versions: $schemaName", e)
            ResponseEntity.ok(emptyList())
        }
    }

    override fun listTables(dataSource: String?): ResponseEntity<List<String>> {
        return try {
            val tables = tableMetadataService.listTables(dataSource)
            ResponseEntity.ok(tables)
        } catch (e: Exception) {
            log.error("Failed to list tables", e)
            ResponseEntity.ok(emptyList())
        }
    }

    override fun listTablesWithColumns(dataSource: String?): ResponseEntity<List<TableDetail>> {
        return try {
            val tables = tableMetadataService.getTablesWithColumns(dataSource)
            ResponseEntity.ok(tables.map { table ->
                TableDetail().apply {
                    name = table.name
                    columns = table.columns.map { col ->
                        TableColumnMetadata().apply {
                            name = col.name
                            dataType = col.dataType
                            sqlType = col.sqlType
                            propertySize = col.size
                            nullable = col.nullable
                            defaultValue = col.defaultValue
                            comment = col.comment
                            ordinalPosition = col.ordinalPosition
                            autoIncrement = col.isAutoIncrement
                        }
                    }.toMutableList()
                }
            })
        } catch (e: Exception) {
            log.error("Failed to list tables with columns", e)
            ResponseEntity.ok(emptyList())
        }
    }

    override fun listTableColumns(tableName: String, dataSource: String?): ResponseEntity<List<TableColumnMetadata>> {
        return try {
            val columns = tableMetadataService.getColumns(tableName, dataSource)
            ResponseEntity.ok(columns.map { col ->
                TableColumnMetadata().apply {
                    name = col.name
                    dataType = col.dataType
                    sqlType = col.sqlType
                    propertySize = col.size
                    nullable = col.nullable
                    defaultValue = col.defaultValue
                    comment = col.comment
                    ordinalPosition = col.ordinalPosition
                    autoIncrement = col.isAutoIncrement
                }
            })
        } catch (e: Exception) {
            log.error("Failed to list columns for table: $tableName", e)
            ResponseEntity.badRequest().build()
        }
    }

    override fun generateTableJsonSchema(tableName: String, dataSource: String?): ResponseEntity<String> {
        return try {
            val jsonSchema = tableMetadataService.generateJsonSchema(tableName, dataSource)
            val jsonStr = com.fluxion.core.util.JsonUtil.serialize(jsonSchema)
            ResponseEntity.ok(jsonStr)
        } catch (e: Exception) {
            log.error("Failed to generate JSON schema for table: $tableName", e)
            ResponseEntity.badRequest().build()
        }
    }

    override fun listForeignKeys(tableName: String, dataSource: String?): ResponseEntity<ForeignKeysResponse> {
        return try {
            val fkResult = tableMetadataService.getForeignKeys(tableName, dataSource)
            ResponseEntity.ok(ForeignKeysResponse().apply {
                incoming = fkResult.incoming.map { fk ->
                    ForeignKeyInfo().apply {
                        fkName = fk.fkName
                        sourceTable = fk.sourceTable
                        sourceColumn = fk.sourceColumn
                        targetTable = fk.targetTable
                        targetColumn = fk.targetColumn
                        keySeq = fk.keySeq
                    }
                }.toMutableList()
                outgoing = fkResult.outgoing.map { fk ->
                    ForeignKeyInfo().apply {
                        fkName = fk.fkName
                        sourceTable = fk.sourceTable
                        sourceColumn = fk.sourceColumn
                        targetTable = fk.targetTable
                        targetColumn = fk.targetColumn
                        keySeq = fk.keySeq
                    }
                }.toMutableList()
            })
        } catch (e: Exception) {
            log.error("Failed to list foreign keys for table: $tableName", e)
            ResponseEntity.badRequest().build()
        }
    }

    private fun toGeneratedSchema(schema: com.fluxion.schema.model.Schema): SchemaDefinition {
        return SchemaDefinition(
            schemaName = schema.name ?: "",
            schemaType = "INPUT,OUTPUT",
            schemaJson = schema.raw,
            schemaFormat = schema.format.code,
            frozen = false,
            scope = "PLATFORM",
            domain = "common"
        )
    }
}