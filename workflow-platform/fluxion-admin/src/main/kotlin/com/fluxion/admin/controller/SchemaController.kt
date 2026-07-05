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
 * Schema 定义管理 REST API
 *
 * 实现 OpenAPI 生成的 SchemasApi 接口，确保前后端契约一致。
 */
@RestController
class SchemaController(
    private val service: WfSchemaService,
    private val schemaMapper: SchemaMapper,
    private val tableMetadataService: TableMetadataService,
    private val securityContext: SecurityContextHelper
) : SchemasApi {

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

    override fun getSchema(schemaName: String): ResponseEntity<SchemaDefinition> {
        val entity = service.getByName(schemaName)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(schemaMapper.toDto(entity))
    }

    override fun createSchema(schemaDefinition: SchemaDefinition): ResponseEntity<SchemaDefinition> {
        val entity = schemaMapper.toEntity(schemaDefinition)
        // 租户权限校验：PRIVATE scope 需要校验 appGroup 访问权限
        if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            securityContext.requireAppGroupAccess(entity.appGroup!!)
        }
        return ResponseEntity.ok(schemaMapper.toDto(service.save(entity)))
    }

    override fun updateSchema(
        schemaName: String,
        schemaDefinition: SchemaDefinition
    ): ResponseEntity<SchemaDefinition> {
        val entity = schemaMapper.toEntity(schemaDefinition)
        // 租户权限校验：校验原始 Schema 的 appGroup 权限
        service.getByName(schemaName)?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        // 若 appGroup 变更，还需校验新 appGroup 的权限
        if (entity.scope == "PRIVATE" && !entity.appGroup.isNullOrBlank()) {
            securityContext.requireAppGroupAccess(entity.appGroup!!)
        }
        return ResponseEntity.ok(schemaMapper.toDto(service.update(schemaName, entity)))
    }

    override fun deleteSchema(schemaName: String): ResponseEntity<Unit> {
        // 租户权限校验
        service.getByName(schemaName)?.appGroup?.let { securityContext.requireAppGroupAccess(it) }
        service.delete(schemaName)
        return ResponseEntity.noContent().build()
    }

    override fun listTables(dataSource: String?): ResponseEntity<List<String>> {
        return ResponseEntity.ok(tableMetadataService.listTables(dataSource))
    }

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

    override fun listSchemaVersions(schemaName: String): ResponseEntity<List<SchemaDefinition>> {
        val list = service.findVersions(schemaName).map { schemaMapper.toDto(it) }
        return ResponseEntity.ok(list)
    }

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

    override fun generateTableJsonSchema(tableName: String, dataSource: String?): ResponseEntity<String> {
        val schema = tableMetadataService.generateJsonSchema(tableName, dataSource)
        return ResponseEntity.ok(JsonUtil.serialize(schema))
    }
}
