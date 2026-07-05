package com.fluxion.core.value

/** 副作用描述（用于 Saga 补偿） */
data class SideEffect(
    /** 副作用类型："DB_UPDATE", "MQ_SEND", "RPC_CALL" */
    val type: String,
    /** 作用目标：表名/topic/服务名 */
    val target: String,
    /** 写入的数据（序列化为 JSON 存储） */
    val payload: Any?,
    /** 补偿函数引用（如 "builtin:dbRollback"） */
    val compensateFunctionRef: String?
)
