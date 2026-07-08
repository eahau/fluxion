package com.fluxion.admin.controller

import com.fluxion.admin.generated.api.DatasourcesApi
import com.fluxion.admin.generated.model.DatasourceColumnInfo
import com.fluxion.admin.generated.model.DatasourceInfo
import com.fluxion.admin.service.TableMetadataService
import org.slf4j.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * REST API that exposes registered JDBC datasources and their table/column metadata.
 *
 * Implements the generated OpenAPI contract `DatasourcesApi`; used primarily by the
 * `db-function-test` feature in the workflow designer, where an author can browse
 * catalogs to pick a table and then ask the `builtin-sql` node to generate a default
 * SELECT or invoke a validation preview.
 *
 * DTO shapes (`DatasourceInfo`, `DatasourceColumnInfo`) are generated from the
 * `datasources` tag inside `doc/openapi.yaml` via OpenAPI Generator.
 */
@RestController
class DataSourceController(
    private val tableMetadataService: TableMetadataService
) : DatasourcesApi {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Return every datasource currently registered in the admin `DataSourceProvider`.
     */
    override fun listDatasources(): ResponseEntity<List<DatasourceInfo>> {
        val names = tableMetadataService.listDataSources()
        val result = names.map { dsName ->
            DatasourceInfo().apply {
                id = dsName
                name = dsName
                domain = "db"
                type = "admin"
            }
        }
        return ResponseEntity.ok(result)
    }

    /**
     * List table names for a given datasource.
     *
     * Swallows JDBC / driver exceptions and returns an empty list because the UI wants
     * to degrade gracefully when a datasource is temporarily offline.
     */
    override fun listDatasourceTables(datasourceId: String): ResponseEntity<List<String>> {
        return try {
            val tables = tableMetadataService.listTables(datasourceId)
            ResponseEntity.ok(tables)
        } catch (e: Exception) {
            log.warn { "Failed to list tables for datasource [$datasourceId]: ${e.message}" }
            ResponseEntity.ok(emptyList())
        }
    }

    /**
     * List column definitions for a specific `datasourceId.tableName` pair.
     *
     * Swallows JDBC exceptions with the same empty-list fallback as `listDatasourceTables`.
     */
    override fun listDatasourceTableColumns(
        datasourceId: String,
        tableName: String
    ): ResponseEntity<List<DatasourceColumnInfo>> {
        return try {
            val columns = tableMetadataService.getColumns(tableName, datasourceId)
            val result = columns.map { col ->
                DatasourceColumnInfo().apply {
                    name = col.name
                    columnName = col.name
                    dataType = col.dataType
                    type = col.dataType
                    comment = col.comment
                    description = col.comment
                }
            }
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.warn(e) { "Failed to list columns for [$datasourceId.$tableName]: ${e.message}" }
            ResponseEntity.ok(emptyList())
        }
    }
}
