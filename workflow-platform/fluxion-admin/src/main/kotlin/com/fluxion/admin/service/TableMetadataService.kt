
package com.fluxion.admin.service

import com.fluxion.builtin.db.DataSourceProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.DatabaseMetaData

/**
 * 数据库表结构元数据查询服务。
 *
 * 通过 JDBC [DatabaseMetaData] 读取指定 DataSource 的表列表与列信息，
 * 用于前端可视化选择表/列，以及从真实表结构生成 JSON Schema。
 * 支持多数据源：未指定 dataSource 时使用默认数据源。
 */
@Service
class TableMetadataService(private val dataSourceProvider: DataSourceProvider) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 列出当前数据库中所有用户表（过滤掉系统/内置表）。
     *
     * @param dataSource 数据源名称，为空则使用默认数据源
     */
    fun listTables(dataSource: String? = null): List<String> {
        return dataSourceProvider.getDataSource(dataSource ?: "default").connection.use { conn ->
            val meta = conn.metaData
            val catalog = conn.catalog
            val schema: String? = null

            meta.getTables(catalog, schema, "%", arrayOf("TABLE")).use { rs ->
                val tables = mutableListOf<String>()
                while (rs.next()) {
                    val tableName = rs.getString("TABLE_NAME") ?: continue
                    if (!isSystemTable(tableName)) {
                        tables.add(tableName)
                    }
                }
                tables.sorted()
            }
        }
    }

    /**
     * 获取指定表的所有列元数据。
     *
     * @param tableName 逻辑表名或真实表名
     * @param dataSource 数据源名称，为空则使用默认数据源
     */
    fun getColumns(tableName: String, dataSource: String? = null): List<ColumnMetadata> {
        require(tableName.isNotBlank()) { "表名不能为空" }
        require(!tableName.contains('`') && !tableName.contains('\'')) { "表名包含非法字符: $tableName" }

        return dataSourceProvider.getDataSource(dataSource ?: "default").connection.use { conn ->
            val meta = conn.metaData
            val catalog = conn.catalog
            val schema: String? = null

            meta.getColumns(catalog, schema, tableName, "%").use { rs ->
                val columns = mutableListOf<ColumnMetadata>()
                while (rs.next()) {
                    columns.add(
                        ColumnMetadata(
                            name = rs.getString("COLUMN_NAME"),
                            dataType = rs.getString("TYPE_NAME"),
                            sqlType = rs.getInt("DATA_TYPE"),
                            size = rs.getInt("COLUMN_SIZE").takeIf { it > 0 },
                            nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                            defaultValue = rs.getString("COLUMN_DEF"),
                            comment = rs.getString("REMARKS"),
                            ordinalPosition = rs.getInt("ORDINAL_POSITION"),
                            isAutoIncrement = "YES".equals(rs.getString("IS_AUTOINCREMENT"), ignoreCase = true)
                        )
                    )
                }
                columns.sortedBy { it.ordinalPosition }
            }
        }
    }

    /**
     * 一次性获取所有用户表及其列信息，用于前端设计器侧边栏展示。
     */
    fun getTablesWithColumns(dataSource: String? = null): List<TableMetadata> {
        return dataSourceProvider.getDataSource(dataSource ?: "default").connection.use { conn ->
            val meta = conn.metaData
            val catalog = conn.catalog
            val schema: String? = null
            val tables = listTables(dataSource)
            tables.map { tableName ->
                TableMetadata(
                    name = tableName,
                    columns = getColumns(tableName, dataSource)
                )
            }
        }
    }

    /**
     * 获取指定表的外键信息（入站：其他表引用本表；出站：本表引用其他表）。
     */
    fun getForeignKeys(tableName: String, dataSource: String? = null): ForeignKeysResult {
        require(tableName.isNotBlank()) { "表名不能为空" }
        require(!tableName.contains('`') && !tableName.contains('\'')) { "表名包含非法字符: $tableName" }

        return dataSourceProvider.getDataSource(dataSource ?: "default").connection.use { conn ->
            val meta = conn.metaData
            val catalog = conn.catalog
            val schema: String? = null

            val exported = mutableListOf<ForeignKeyMetadata>()
            meta.getExportedKeys(catalog, schema, tableName).use { rs ->
                while (rs.next()) {
                    exported.add(
                        ForeignKeyMetadata(
                            fkName = rs.getString("FK_NAME"),
                            sourceTable = rs.getString("FKTABLE_NAME"),
                            sourceColumn = rs.getString("FKCOLUMN_NAME"),
                            targetTable = rs.getString("PKTABLE_NAME"),
                            targetColumn = rs.getString("PKCOLUMN_NAME"),
                            keySeq = rs.getInt("KEY_SEQ")
                        )
                    )
                }
            }

            val imported = mutableListOf<ForeignKeyMetadata>()
            meta.getImportedKeys(catalog, schema, tableName).use { rs ->
                while (rs.next()) {
                    imported.add(
                        ForeignKeyMetadata(
                            fkName = rs.getString("FK_NAME"),
                            sourceTable = rs.getString("FKTABLE_NAME"),
                            sourceColumn = rs.getString("FKCOLUMN_NAME"),
                            targetTable = rs.getString("PKTABLE_NAME"),
                            targetColumn = rs.getString("PKCOLUMN_NAME"),
                            keySeq = rs.getInt("KEY_SEQ")
                        )
                    )
                }
            }

            ForeignKeysResult(
                incoming = exported.sortedWith(compareBy({ it.fkName }, { it.keySeq })),
                outgoing = imported.sortedWith(compareBy({ it.fkName }, { it.keySeq }))
            )
        }
    }

    /**
     * 根据表结构生成 JSON Schema（对象）。
     */
    fun generateJsonSchema(tableName: String, dataSource: String? = null): Map<String, Any?> {
        val columns = getColumns(tableName, dataSource)
        require(columns.isNotEmpty()) { "Table not found or has no columns: $tableName" }

        val properties = LinkedHashMap<String, Map<String, Any?>>()
        val required = mutableListOf<String>()

        columns.forEach { col ->
            val jsonType = sqlTypeToJsonType(col.sqlType, col.dataType)
            val prop = mutableMapOf<String, Any?>(
                "type" to jsonType,
                "description" to buildDescription(col)
            )
            if (col.isAutoIncrement) {
                prop["autoIncrement"] = true
            }
            if (!col.nullable && !col.isAutoIncrement && col.defaultValue == null) {
                required.add(col.name)
            }
            properties[col.name] = prop
        }

        return mapOf(
            "type" to "object",
            "title" to tableName,
            "description" to "Auto-generated schema from table: $tableName",
            "properties" to properties,
            "required" to required
        )
    }

    private fun sqlTypeToJsonType(sqlType: Int, dataType: String?): String {
        return when (sqlType) {
            java.sql.Types.BIGINT, java.sql.Types.INTEGER, java.sql.Types.SMALLINT,
            java.sql.Types.TINYINT -> "integer"
            java.sql.Types.NUMERIC, java.sql.Types.DECIMAL, java.sql.Types.FLOAT,
            java.sql.Types.REAL, java.sql.Types.DOUBLE -> "number"
            java.sql.Types.BOOLEAN, java.sql.Types.BIT -> "boolean"
            java.sql.Types.DATE, java.sql.Types.TIME, java.sql.Types.TIMESTAMP -> "string"
            java.sql.Types.VARCHAR, java.sql.Types.CHAR, java.sql.Types.NVARCHAR,
            java.sql.Types.LONGVARCHAR, java.sql.Types.CLOB -> "string"
            else -> when (dataType?.lowercase()) {
                "json" -> "object"
                else -> "string"
            }
        }
    }

    private fun buildDescription(col: ColumnMetadata): String {
        val parts = mutableListOf<String>()
        col.dataType?.let { parts.add(it) }
        if (col.isAutoIncrement) parts.add("auto increment")
        if (!col.nullable) parts.add("not null")
        col.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return if (parts.isEmpty()) col.name else parts.joinToString(", ")
    }

    /**
     * 过滤数据库内置表（Flyway 历史表、MySQL 系统表等）。
     */
    private fun isSystemTable(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("flyway_schema_history") ||
            lower.startsWith("information_schema") ||
            lower.startsWith("performance_schema") ||
            lower.startsWith("mysql") ||
            lower.startsWith("sys") ||
            lower.startsWith("pg_") ||
            (lower.startsWith("trace_")) ||
            lower == "users" ||
            lower == "roles"
    }
}

/**
 * 表元数据（含列）。
 */
data class TableMetadata(
    val name: String,
    val columns: List<ColumnMetadata>
)

/**
 * 列元数据。
 */
data class ColumnMetadata(
    val name: String,
    val dataType: String?,
    val sqlType: Int,
    val size: Int?,
    val nullable: Boolean,
    val defaultValue: String?,
    val comment: String?,
    val ordinalPosition: Int,
    val isAutoIncrement: Boolean
)

/**
 * 外键元数据。
 */
data class ForeignKeyMetadata(
    val fkName: String?,
    val sourceTable: String,
    val sourceColumn: String,
    val targetTable: String,
    val targetColumn: String,
    val keySeq: Int
)

/**
 * 外键关系结果。
 */
data class ForeignKeysResult(
    val incoming: List<ForeignKeyMetadata>,
    val outgoing: List<ForeignKeyMetadata>
)
