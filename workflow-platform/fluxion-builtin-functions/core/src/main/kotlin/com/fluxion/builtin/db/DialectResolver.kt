package com.fluxion.builtin.db

import com.fluxion.builtin.db.dialect.MySqlDialect
import com.fluxion.builtin.db.dialect.SqlDialect
import com.fluxion.builtin.db.dialect.SqlDialectFactory
import javax.sql.DataSource

/**
 * SQL 方言解析器 SPI。
 *
 * 用于在零 Spring 的 core 模块中解耦方言探测逻辑与 DataSource 查找逻辑。
 * Spring Boot 装配层提供基于 Environment 的实现，非 Spring 场景由用户自行实现。
 */
fun interface DialectResolver {

    /**
     * 根据数据源名称和数据源实例解析对应的 SQL 方言。
     *
     * @param name 数据源名称
     * @param dataSource 数据源实例
     * @return 对应的 SQL 方言
     */
    fun resolve(name: String, dataSource: DataSource): SqlDialect
}

/**
 * 默认方言解析器：始终返回 MySQL 方言，保持向后兼容。
 */
object DefaultDialectResolver : DialectResolver {
    override fun resolve(name: String, dataSource: DataSource): SqlDialect = MySqlDialect
}

/**
 * 基于 JDBC URL + 连接元数据的方言解析器。
 *
 * 探测策略：
 * 1. 优先通过 [urlProvider] 获取 JDBC URL，使用 [SqlDialectFactory.fromJdbcUrl] 判断
 * 2. 若 URL 不可用，打开连接读取 [DatabaseMetaData.getDatabaseProductName]
 * 3. 若仍失败，返回 MySQL 方言
 *
 * @param urlProvider 根据数据源名称返回 JDBC URL；返回 null 表示无法从配置获取
 */
class UrlAndMetadataDialectResolver(
    private val urlProvider: (String) -> String?
) : DialectResolver {

    override fun resolve(name: String, dataSource: DataSource): SqlDialect {
        // 1. 优先从 JDBC URL 判断
        urlProvider(name)?.takeIf { it.isNotBlank() }?.let { url ->
            return SqlDialectFactory.fromJdbcUrl(url)
        }
        // 2. 回退到连接元数据
        try {
            dataSource.connection.use { conn ->
                val productName = conn.metaData.databaseProductName
                return SqlDialectFactory.fromProductName(productName)
            }
        } catch (_: Exception) {
            // 忽略，最后兜底
        }
        // 3. 默认 MySQL
        return MySqlDialect
    }
}
