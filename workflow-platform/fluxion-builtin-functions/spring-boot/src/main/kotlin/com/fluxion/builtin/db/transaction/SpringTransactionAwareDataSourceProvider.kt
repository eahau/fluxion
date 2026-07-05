package com.fluxion.builtin.db.transaction

import com.fluxion.builtin.db.DataSourceProvider
import com.fluxion.builtin.db.dialect.SqlDialect
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

/**
 * Spring 运行环境下的数据源提供者包装。
 *
 * 使用 Spring 的 [TransactionAwareDataSourceProxy] 包装真实数据源，
 * 使 DB 函数能够自动加入 Spring 事务同步器所管理的事务连接。
 */
class SpringTransactionAwareDataSourceProvider(private val delegate: DataSourceProvider) : DataSourceProvider {

    override fun getDataSource(name: String): DataSource =
        TransactionAwareDataSourceProxy(delegate.getDataSource(name))

    override fun getDialect(name: String): SqlDialect = delegate.getDialect(name)
}
