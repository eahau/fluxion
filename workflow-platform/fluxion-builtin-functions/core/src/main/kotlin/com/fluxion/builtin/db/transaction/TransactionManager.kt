package com.fluxion.builtin.db.transaction

/**
 * 事务管理器 SPI。
 *
 * 屏蔽底层 JDBC / Spring / JTA 等事务实现差异，为 [com.fluxion.core.decorator.NodeDecorator]
 * 提供统一的事务边界。
 */
interface TransactionManager {

    /**
     * 在指定数据源的事务边界内执行 [action]。
     *
     * @param dataSourceName 数据源名称
     * @param definition 事务定义（传播、隔离、超时、只读）
     * @param action 实际执行业务逻辑
     * @return 业务逻辑返回值
     */
    fun <T> execute(dataSourceName: String, definition: TransactionDefinition, action: () -> T): T

    /**
     * 当前线程是否已在指定数据源上存在活动事务。
     */
    fun isActive(dataSourceName: String): Boolean
}

/**
 * 事务定义。
 *
 * 字段语义与 Spring 的 [org.springframework.transaction.TransactionDefinition] 对齐，
 * 使 Spring 适配器可以零损耗地完成映射。
 */
data class TransactionDefinition(
    val propagation: Int = Propagation.REQUIRED,
    val isolation: Int? = null,
    val timeout: Int = -1,
    val readOnly: Boolean = false,
    val name: String? = null
)

/**
 * 传播行为常量。
 *
 * 数值与 Spring `TransactionDefinition` 中的传播常量一一对应。
 */
object Propagation {
    const val REQUIRED = 0
    const val SUPPORTS = 1
    const val MANDATORY = 2
    const val REQUIRES_NEW = 3
    const val NOT_SUPPORTED = 4
    const val NEVER = 5
    const val NESTED = 6
}

/**
 * 隔离级别常量。
 *
 * 数值与 Spring `TransactionDefinition` 中的隔离常量一一对应，
 * 同时兼容 [java.sql.Connection] 的标准隔离级别。
 */
object TransactionIsolation {
    const val DEFAULT = -1
    const val READ_UNCOMMITTED = java.sql.Connection.TRANSACTION_READ_UNCOMMITTED
    const val READ_COMMITTED = java.sql.Connection.TRANSACTION_READ_COMMITTED
    const val REPEATABLE_READ = java.sql.Connection.TRANSACTION_REPEATABLE_READ
    const val SERIALIZABLE = java.sql.Connection.TRANSACTION_SERIALIZABLE
}
