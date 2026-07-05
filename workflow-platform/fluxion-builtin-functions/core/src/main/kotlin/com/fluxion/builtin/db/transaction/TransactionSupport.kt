package com.fluxion.builtin.db.transaction

import com.fluxion.core.model.TransactionConfig
import com.fluxion.core.model.booleanParam
import com.fluxion.core.model.intParam
import com.fluxion.core.model.stringParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 事务装饰器公共逻辑。
 *
 * 抽取 [TransactionDecorator] 与 [WorkflowTransactionDecorator] 重复的事务定义解析、
 * 传播/隔离级别转换、事务执行桥接等逻辑，使工作流级与节点级装饰器共享同一套核心实现。
 */
object TransactionSupport {

    /**
     * 根据参数（以及可选的 [TransactionConfig]）解析数据源名称与事务定义。
     *
     * @param params 装饰器参数
     * @param name 事务名称（日志/显示用）
     * @param transactionConfig 工作流级显式事务配置，存在时优先使用
     * @return Pair<数据源名称, 事务定义>
     */
    fun resolveDefinition(
        params: Map<String, Any>?,
        name: String?,
        transactionConfig: TransactionConfig? = null
    ): Pair<String, TransactionDefinition> {
        val dataSourceName = params.stringParam("dataSource") ?: "default"
        val definition = when (transactionConfig) {
            null -> buildDefinitionFromParams(params, name)
            else -> buildDefinitionFromConfig(transactionConfig, name)
        }
        return dataSourceName to definition
    }

    /**
     * 在阻塞式 [WorkflowFunction] 内部开启事务边界。
     */
    fun <T> execute(
        transactionManager: TransactionManager,
        dataSourceName: String,
        definition: TransactionDefinition,
        action: () -> T
    ): T = transactionManager.execute(dataSourceName, definition, action)

    /**
     * 在 suspend 工作流级装饰器内部开启事务边界。
     *
     * 事务的开启/提交/回滚在 [Dispatchers.IO] 中执行，避免阻塞业务协程；
     * [action] 在 IO 调度器内通过 [runBlocking] 桥接为阻塞回调。
     */
    suspend fun <T> executeSuspending(
        transactionManager: TransactionManager,
        dataSourceName: String,
        definition: TransactionDefinition,
        action: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        transactionManager.execute(dataSourceName, definition) { runBlocking { action() } }
    }

    /** 将字符串传播行为解析为 [Propagation] 常量。 */
    fun parsePropagation(value: String): Int = when (value.uppercase()) {
        "REQUIRED" -> Propagation.REQUIRED
        "SUPPORTS" -> Propagation.SUPPORTS
        "MANDATORY" -> Propagation.MANDATORY
        "REQUIRES_NEW" -> Propagation.REQUIRES_NEW
        "NOT_SUPPORTED" -> Propagation.NOT_SUPPORTED
        "NEVER" -> Propagation.NEVER
        "NESTED" -> Propagation.NESTED
        else -> throw IllegalArgumentException("Unknown propagation: $value")
    }

    /** 将字符串隔离级别解析为 [TransactionIsolation] 常量。 */
    fun parseIsolation(value: String): Int? = when (value.uppercase()) {
        "DEFAULT" -> TransactionIsolation.DEFAULT
        "READ_UNCOMMITTED" -> TransactionIsolation.READ_UNCOMMITTED
        "READ_COMMITTED" -> TransactionIsolation.READ_COMMITTED
        "REPEATABLE_READ" -> TransactionIsolation.REPEATABLE_READ
        "SERIALIZABLE" -> TransactionIsolation.SERIALIZABLE
        else -> null
    }

    private fun buildDefinitionFromParams(
        params: Map<String, Any>?,
        name: String?
    ): TransactionDefinition = TransactionDefinition(
        propagation = parsePropagation(params.stringParam("propagation") ?: "REQUIRED"),
        isolation = parseIsolation(params.stringParam("isolation") ?: ""),
        timeout = params.intParam("timeout", -1),
        readOnly = params.booleanParam("readOnly", false),
        name = name
    )

    private fun buildDefinitionFromConfig(
        config: TransactionConfig,
        name: String?
    ): TransactionDefinition = TransactionDefinition(
        propagation = config.propagationBehavior,
        isolation = config.isolationLevel,
        timeout = config.timeoutMs,
        readOnly = false,
        name = config.transactionName ?: name
    )
}
