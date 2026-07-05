package com.fluxion.core.function.external

/**
 * 外部函数调用响应。
 */
data class ExternalFunctionResponse(
    /** 调用结果输出 */
    val output: Any?,
    /** 是否成功 */
    val success: Boolean = true,
    /** 错误码（失败时） */
    val errorCode: String? = null,
    /** 错误信息（失败时） */
    val errorMessage: String? = null
)
