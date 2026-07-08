package com.fluxion.admin.service

import com.fluxion.builtin.db.DataSourceProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.DatabaseMetaData

/**
 * Introspect JDBC [DatabaseMetaData] to produce admin-facing schema tooling:
 *   - list registered DataSource names
 *   - enumerate tables / columns / foreign keys in user schemas (filtering out
 *     system catalogs, Flyway history, and default Fluxion auth tables)
 *   - auto-generate JSON-Schema documents for any table column set (used by
 *     workflow builders when scaffolding dbExecute inputs)
 *
 * Driven entirely by JDBC so it works across MySQL / PostgreSQL / H2 without any
 * vendor-specific SQL.
 *
 * Collaborates with: [DataSourceProvider] (builtin multi-tenant DataSource registry).
 */
@Service
class TableMetadataService(private val dataSourceProvider: DataSourceProvider) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Enumerate every DataSource name registered with the builtin provider. */
    fun listDataSources(): List<String> = dataSourceProvider.listDataSourceNames()

    /**
     * List all non-system tables in a DataSource. Defaults to the `"default"`
     * DataSource when `dataSource` is not supplied. Results are sorted by name
     * for stable admin UI rendering.
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
                        tables += tableName
                    }
                }
                tables.sorted()
            }
        }
    }

    /**
     * Enumerate column metadata for a given table. Results are sorted by the JDBC
     * ORDINAL_POSITION (left-to-right column order). `tableName` is rejected if it
     * contains backticks or single quotes to avoid accidental SQL injection in
     * drivers that do not properly parameterise `meta.getColumns(...)`.
     */
    fun getColumns(tableName: String, dataSource: String? = null): List<ColumnMetadata> {
        require(tableName.isNotBlank()) { "Table name cannot be blank" }
        require(!tableName.contains('`') && !tableName.contains('\'')) { "Table name contains illegal characters: $tableName" }

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
     * Batch helper: every user-visible table paired with its columns.
     * Used by the schema visualiser / ER-diagram tools.
     */
    fun getTablesWithColumns(dataSource: String? = null): List<TableMetadata> {
        return dataSourceProvider.getDataSource(dataSource ?: "default").connection.use { conn ->
            val tables = listTables(dataSource)
            tables.map { tn ->
                TableMetadata(
                    name = tn,
                    columns = getColumns(tn, dataSource)
                )
            }
        }
    }

    /**
     * Foreign-key inspection for a single table. Returns both directions:
     *   - [ForeignKeysResult.incoming]: keys exported FROM this table (i.e. other
     *     tables whose FK references our PK)
     *   - [ForeignKeysResult.outgoing]: keys imported INTO this table (i.e. FKs
     *     this table declares against other tables' primary keys)
     *
     * Results within each list are sorted by FK name then key sequence so
     * multi-column foreign keys reproduce in order.
     */
    fun getForeignKeys(tableName: String, dataSource: String? = null): ForeignKeysResult {
        require(tableName.isNotBlank()) { "Table name cannot be blank" }
        require(!tableName.contains('`') && !tableName.contains('\'')) { "Table name contains illegal characters: $tableName" }

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
     * Build a JSON-Schema 2020-12 style document for `tableName`.
     *
     * Produced shape:
     *   - top-level `object` with title / description / properties / required
     *   - one property per column with:
     *       * `type` (mapped via [sqlTypeToJsonType])
     *       * `description` human-readable column info (DB type + nullability + COMMENT)
     *       * `autoIncrement: true` flag when applicable
     *   - `required` array includes every non-nullable column that has no default
     *     and is NOT auto-increment (those are generated server side)
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
            // Required = non-nullable, non-auto-increment, no default
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

    /**
     * Map a [java.sql.Types] constant to the best JSON-Schema type string.
     *
     * Fallback strategy: if the numeric JDBC type is not in the well-known list
     * (vendor-specific extensions), inspect the driver-reported string type name
     * — e.g. PostgreSQL `json` columns get mapped to JSON-Schema `object` while
     * everything else falls back to `string`.
     */
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

    /**
     * Build the human-readable `description` string that accompanies each column
     * in the generated JSON schema: `<TYPE_NAME> | auto increment | not null | <REMARKS>`.
     */
    private fun buildDescription(col: ColumnMetadata): String {
        val parts = mutableListOf<String>()
        col.dataType?.let { parts.add(it) }
        if (col.isAutoIncrement) parts.add("auto increment")
        if (!col.nullable) parts.add("not null")
        col.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return if (parts.isEmpty()) col.name else parts.joinToString(", ")
    }

    /**
     * Table name filter used by [listTables]. Hides:
     *   - Flyway schema-history tables
     *   - standard SQL/RDBMS system catalogs (information_schema / performance_schema
     *     / pg_catalog prefix / mysql / sys)
     *   - Fluxion built-in auth tables (users / roles) — those are administered
     *     through the dedicated User/Role screens
     *   - Internal trace_* tables
     */
    private fun isSystemTable(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("flyway_schema_history") ||
            lower.startsWith("information_schema") ||
            lower.startsWith("performance_schema") ||
            lower.startsWith("mysql") ||
            lower.startsWith("sys") ||
            lower.startsWith("pg_") ||
            lower.startsWith("trace_") ||
            lower == "users" ||
            lower == "roles"
    }
}

/** Aggregate: table name + its column list. */
data class TableMetadata(
    val name: String,
    val columns: List<ColumnMetadata>
)

/** Per-column metadata as reported by JDBC DatabaseMetaData.getColumns. */
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

/** One foreign-key column pair (part of a potentially multi-column FK). */
data class ForeignKeyMetadata(
    val fkName: String?,
    val sourceTable: String,
    val sourceColumn: String,
    val targetTable: String,
    val targetColumn: String,
    val keySeq: Int
)

/** Two-directional FK result set for a single table. */
data class ForeignKeysResult(
    val incoming: List<ForeignKeyMetadata>,
    val outgoing: List<ForeignKeyMetadata>
)
