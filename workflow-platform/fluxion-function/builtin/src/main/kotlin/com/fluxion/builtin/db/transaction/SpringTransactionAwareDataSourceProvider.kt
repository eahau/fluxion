package com.fluxion.builtin.db.transaction

import com.fluxion.builtin.db.DataSourceProvider
import com.fluxion.builtin.db.dialect.SqlDialect
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

/**
 * Data-source provider wrapper that integrates raw DataSources with Spring's
 * transaction-synchronization manager.
 *
 * Wraps each resolved [DataSource] in Spring's
 * [TransactionAwareDataSourceProxy]. The proxy ensures that when a
 * transaction context exists (driven by [TransactionDecorator] /
 * [WorkflowTransactionDecorator]), subsequent `dataSource.connection` calls
 * inside DB functions return the *same* JDBC connection that the platform
 * transaction manager is already holding—so writes become atomic with the
 * surrounding transaction boundary instead of auto-committing in isolation.
 *
 * Dialect / name listing is delegated unchanged to the delegate because those
 * operations don't need transaction participation.
 */
class SpringTransactionAwareDataSourceProvider(private val delegate: DataSourceProvider) : DataSourceProvider {

    override fun getDataSource(name: String): DataSource =
        TransactionAwareDataSourceProxy(delegate.getDataSource(name))

    override fun getDialect(name: String): SqlDialect = delegate.getDialect(name)

    override fun listDataSourceNames(): List<String> = delegate.listDataSourceNames()
}
