package com.fluxion.core.model

/**
 * 事务配置 — 零框架依赖，使用 java.sql.Connection 中的隔离级别常量
 */
data class TransactionConfig(
    var workflowId: String? = null,
    var executionId: String? = null,
    /** 事务名称（用于日志） */
    var transactionName: String? = null,
    /**
     * 隔离级别（使用 java.sql.Connection 常量）
     * 默认：READ_COMMITTED = 2
     */
    var isolationLevel: Int = java.sql.Connection.TRANSACTION_READ_COMMITTED,
    /**
     * 传播行为（语义定义，不依赖 Spring）
     * 0 = REQUIRED, 3 = REQUIRES_NEW, 6 = NESTED
     */
    var propagationBehavior: Int = PropagationBehavior.REQUIRED,
    /** 超时时间（ms，0=无限制） */
    var timeoutMs: Int = 30_000
) {
    /** 传播行为常量（对应 Spring TransactionDefinition，但不依赖 Spring） */
    object PropagationBehavior {
        const val REQUIRED = 0
        const val SUPPORTS = 1
        const val MANDATORY = 2
        const val REQUIRES_NEW = 3
        const val NOT_SUPPORTED = 4
        const val NEVER = 5
        const val NESTED = 6
    }
}
