package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.SchemasApi
import com.fluxion.admin.generated.model.ForeignKeyInfo
import com.fluxion.admin.generated.model.ForeignKeysResponse
import com.fluxion.admin.generated.model.PageResponseSchemaDefinition
import com.fluxion.admin.generated.model.SchemaDefinition
import com.fluxion.admin.generated.model.TableColumnMetadata
import com.fluxion.admin.generated.model.TableDetail
import com.fluxion.admin.mapper.SchemaMapper
import com.fluxion.admin.security.SecurityContextHelper
import com.fluxion.admin.service.TableMetadataService
import com.fluxion.admin.service.WfSchemaService
import com.fluxion.core.util.JsonUtil
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * Schema definition & RDBMS metadata REST controller (implements OpenAPI-generated [SchemasApi]).
 *
 * Two distinct functional areas share this controller (grouped under the Schemas
 * admin nav section):
 *   1. **WfSchema CRUD** — reusable named JSON-Schema documents (referenced by
 *      workflows and other schemas via `$ref: "schema:<name>"`). Enforces tenant
 *      gating for PRIVATE-scope schemas via [SecurityContextHelper].
 *   2. **Live DB introspection** — enumerate DataSources, tables, columns, FKs and
 *      auto-generate JSON-Schema from any table via JDBC `DatabaseMetaData`. Used by
 *      the DAG builder to scaffold `dbExecute` node inputs.
 *
 * Collaborates with: WfSchemaService (schema CRUD + worker push), SchemaMapper
 * (DTO↔Entity), TableMetadataService (JDBC introspection), SecurityContextHelper
 * (tenant gating on PRIVATE scope resources).
 */
@RestController
class SchemaController(
    private val service: WfSchemaService,
    private val schemaMapper: SchemaMapper,
    private val tableMetadataService: TableMetadataService,
    private val securityContext: SecurityContextHelper
) : SchemasApi {

    /**
     * Paginated schema list page. Delegates to the optimised summary projection
     * (schemaJson is NOT loaded — ~90% smaller payload than the full entity).
     */
    override fun listSchemas(
        keyword: String?,
        schemaType: String?,
        page: Int,
        pageSize: Int
    ): ResponseEntity<PageResponseSchemaDefinition> {
        val size = pageSize.coerceAtMost(100)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val result = service.searchSummaries(keyword, schemaType, pageable)
        return ResponseEntity.ok(PageResponseSchemaDefinition().apply {
            total = result.totalElements
            this.page = result.number
            this.pageSize = size
            list = result.content.map { schemaMapper.toDto(it) }.toMutableList()
        })
    }

    /** Fetch a single full schema definition (including the large schemaJson column). */
    override fun getSchema(schemaName: String): ResponseEntity<SchemaDefinition> {
        val entity = service.getByName(schemaName)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(schemaMapper.toDto(entity))
    }

    /**
     * Create a new schema. PRIVATE-scope resources require the caller to have
     * access to the target app_group (checked via SecurityContextHelper).
     */
    override fun createSchema(schemaDefinition: SchemaDefinition): ResponseEntity<SchemaDefinition> {
        val entity = schemaMapper.toEntity(schemaDefinition)
        if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            securityContext.requireAppGroupAccess(entity.appGroup!!)
        }
        return ResponseEntity.ok(schemaMapper.toDto(service.save(entity)))
    }

    /**
     * Update an existing schema. Validates access against both the OLD appGroup
     * (before mutation) and the NEW appGroup (if the caller is moving the schema
     * to another tenant).
     */
    override fun updateSchema(
        schemaName: String,
        schemaDefinition: SchemaDefinition
    ): ResponseEntity<SchemaDefinition> {
        val entity = schemaMapper.toEntity(schemaDefinition)
        service.getByName(schemaName)?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            securityContext.requireAppGroupAccess(entity.appGroup!!)
        }
        return ResponseEntity.ok(schemaMapper.toDto(service.update(schemaName, entity)))
    }

    /** Delete a schema by name. Checks tenant access against the original appGroup. */
    override fun deleteSchema(schemaName: String): ResponseEntity<Unit> {
        service.getByName(schemaName)?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.delete(schemaName)
        return ResponseEntity.noContent().build()
    }

    /** Enumerate non-system table names in the target (or default) DataSource. */
    override fun listTables(dataSource: String?): ResponseEntity<List<String>> {
        return ResponseEntity.ok(tableMetadataService.listTables(dataSource))
    }

    /** Introspect column metadata for one table (used by the schema JSON-Schema generator). */
    override fun listTableColumns(tableName: String, dataSource: String?): ResponseEntity<List<TableColumnMetadata>> {
        val columns = tableMetadataService.getColumns(tableName, dataSource).map {
            TableColumnMetadata().apply {
                name = it.name
                dataType = it.dataType
                sqlType = it.sqlType
                propertySize = it.size
                nullable = it.nullable
                defaultValue = it.defaultValue
                comment = it.comment
                ordinalPosition = it.ordinalPosition
                autoIncrement = it.isAutoIncrement
            }
        }
        return ResponseEntity.ok(columns)
    }

    /** Historical versions for a schema (currently returns the single current row). */
    override fun listSchemaVersions(schemaName: String): ResponseEntity<List<SchemaDefinition>> {
        val list = service.findVersions(schemaName).map { schemaMapper.toDto(it) }
        return ResponseEntity.ok(list)
    }

    /** Batch helper: returns every user-visible table together with its columns. */
    override fun listTablesWithColumns(dataSource: String?): ResponseEntity<List<TableDetail>> {
        val tables = tableMetadataService.getTablesWithColumns(dataSource).map { table ->
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
        }
        return ResponseEntity.ok(tables)
    }

    /** Incoming + outgoing FK inspection for ER-diagram tooling. */
    override fun listForeignKeys(tableName: String, dataSource: String?): ResponseEntity<ForeignKeysResponse> {
        val result = tableMetadataService.getForeignKeys(tableName, dataSource)
        val toInfo = { fk: com.fluxion.admin.service.ForeignKeyMetadata ->
            ForeignKeyInfo().apply {
                fkName = fk.fkName
                sourceTable = fk.sourceTable
                sourceColumn = fk.sourceColumn
                targetTable = fk.targetTable
                targetColumn = fk.targetColumn
                keySeq = fk.keySeq
            }
        }
        return ResponseEntity.ok(ForeignKeysResponse().apply {
            incoming = result.incoming.map(toInfo).toMutableList()
            outgoing = result.outgoing.map(toInfo).toMutableList()
        })
    }

    /** Generate a JSON-Schema document for a given table (stringified JSON). */
    override fun generateTableJsonSchema(tableName: String, dataSource: String?): ResponseEntity<String> {
        val schema = tableMetadataService.generateJsonSchema(tableName, dataSource)
        return ResponseEntity.ok(JsonUtil.serialize(schema))
    }
}
