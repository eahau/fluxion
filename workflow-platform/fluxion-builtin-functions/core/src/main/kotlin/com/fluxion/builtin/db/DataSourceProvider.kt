package com.fluxion.builtin.db

import com.fluxion.builtin.db.dialect.MySqlDialect
import com.fluxion.builtin.db.dialect.SqlDialect
import javax.sql.DataSource

/**
 * 数据源提供器 SPI。
 *
 * 用于在零 Spring 的 workflow-builtin-functions-core 中支持按名称获取数据源，
 * 以实现分库场景下的数据源路由。Spring Boot 装配层负责注入具体实现。
 */
fun interface DataSourceProvider {

    /**
     * 根据数据源名称获取 [DataSource]。
     *
     * @param name 数据源名称；若为空或不存在，实现方应返回默认数据源或抛出异常。
     * @return 对应的数据源
     */
    fun getDataSource(name: String): DataSource

    /**
     * 根据数据源名称获取对应的 SQL 方言。
     *
     * 默认实现返回 MySQL 方言，保持向后兼容。
     * Spring Boot 装配层应覆写此方法，根据实际数据库类型返回正确的方言。
     *
     * @param name 数据源名称
     * @return 对应的 SQL 方言
     */
    fun getDialect(name: String): SqlDialect = MySqlDialect
}
