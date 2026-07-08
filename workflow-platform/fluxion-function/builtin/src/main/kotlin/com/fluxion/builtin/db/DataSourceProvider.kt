package com.fluxion.builtin.db

import com.fluxion.builtin.db.dialect.MySqlDialect
import com.fluxion.builtin.db.dialect.SqlDialect
import javax.sql.DataSource

/**
 * SPI for looking up [DataSource] instances and their SQL dialect by logical name.
 *
 * Decouples dbExecute / transaction decorators from the concrete wiring layer.
 * In non-Spring environments (core tests, CLI) a simple in-code implementation
 * can be used; in Spring Boot the auto-configuration module wraps the Spring
 * application context's DataSource beans.
 *
 * Routing by name supports multi-database deployments where different nodes /
 * different call sites target different physical databases.
 */
fun interface DataSourceProvider {

    /**
     * Resolves a [DataSource] by logical name.
     *
     * @param name logical data source name; blank / unknown names should fall
     *   back to a default source or throw—concrete implementations decide.
     * @return resolved DataSource (never null)
     * @throws IllegalStateException if no source can be resolved
     */
    fun getDataSource(name: String): DataSource

    /**
     * Resolves the [SqlDialect] for a named data source.
     *
     * Defaults to [MySqlDialect] for backward compatibility; Spring Boot
     * auto-config overrides this to use dialect detection from JDBC metadata.
     */
    fun getDialect(name: String): SqlDialect = MySqlDialect

    /**
     * Lists the names of every data source currently known to this provider.
     *
     * Defaults to a single `"default"` entry; Spring Boot auto-config overrides
     * this with the actual map of named beans.
     */
    fun listDataSourceNames(): List<String> = listOf("default")
}
