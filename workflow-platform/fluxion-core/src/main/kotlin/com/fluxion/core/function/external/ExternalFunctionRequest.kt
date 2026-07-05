package com.fluxion.core.function.external

/**
 * 外部函数调用请求。
 */
data class ExternalFunctionRequest(
    /** 调用配置 */
    val config: ExternalFunctionConfig,
    /** 当前节点的输入数据（通常为 Map 或 null） */
    val input: Any?,
    /** 函数引用名 */
    val functionRef: String,
    /** 函数元信息中的描述 */
    val description: String? = null
)
