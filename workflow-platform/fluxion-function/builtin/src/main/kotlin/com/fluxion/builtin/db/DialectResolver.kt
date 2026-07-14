package com.fluxion.builtin.db

import com.fluxion.builtin.db.dialect.MySqlDialect
import com.fluxion.builtin.db.dialect.SqlDialect
import com.fluxion.builtin.db.dialect.SqlDialectFactory
import javax.sql.DataSource

/**
 * SPI that determines the correct [SqlDialect] for a given [DataSource].
 *
 * Kept as a pure interface so core dbExecute code has zero Spring dependency.
 * The Spring Boot auto-config module provides an
 * [UrlAndMetadataDialectResolver] backed by the environment.
 */
fun interface DialectResolver {

    /**
     * Resolves dialect for a data source.
     *
     * @param name logical data source name (may be used to key config lookups)
     * @param dataSource live data source instance (for metadata probing)
     */
    fun resolve(name: String, dataSource: DataSource): SqlDialect
}

/**
 * No-op fallback resolver that always returns [MySqlDialect] (backward-compat).
 */
object DefaultDialectResolver : DialectResolver {
    override fun resolve(name: String, dataSource: DataSource): SqlDialect = MySqlDialect
}

/**
 * Resolver that first inspects the JDBC URL (fast path) and falls back to
 * opening a connection and reading `DatabaseMetaData#getDatabaseProductName`.
 *
 * Detection order:
 * 1. [urlProvider] → [SqlDialectFactory.fromJdbcUrl]
 * 2. Live connection metadata → [SqlDialectFactory.fromProductName]
 * 3. Final fallback: MySQL dialect
 *
 * @param urlProvider returns the JDBC URL for a logical name, or `null` if
 *   URL cannot be determined from config alone.
 */
class UrlAndMetadataDialectResolver(
    private val urlProvider: (String) -> String?
) : DialectResolver {

    override fun resolve(name: String, dataSource: DataSource): SqlDialect {
        // Fast path avoids opening a connection when the URL is known from env.
        urlProvider(name)?.takeIf { it.isNotBlank() }?.let { url ->
            return SqlDialectFactory.fromJdbcUrl(url)
        }
        // Slow path: open a connection and inspect the driver metadata.
        try {
            dataSource.connection.use { conn ->
                val productName = conn.metaData.databaseProductName
                return SqlDialectFactory.fromProductName(productName)
            }
        } catch (_: Exception) {
            // Swallowed on purpose—dialect detection is best-effort; MySQL
            // syntax is the closest to portable across remaining engines.
        }
        return MySqlDialect
    }
}
