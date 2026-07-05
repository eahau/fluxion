package com.fluxion.admin.exception

import org.springframework.http.HttpStatus

/**
 * Admin 后台统一业务异常。
 *
 * 通过 [errorCode] 定位错误类型，[httpStatus] 决定 HTTP 响应状态码，
 * [message] 为面向用户的可读错误描述。由 [GlobalExceptionHandler] 统一捕获并转换为
 * [AdminErrorResponse] 返回给前端。
 */
class WorkflowAdminException(
    val errorCode: String,
    message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /** 通用参数校验错误 */
        fun badRequest(code: String = "BAD_REQUEST", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.BAD_REQUEST)

        /** 资源未找到 */
        fun notFound(code: String = "NOT_FOUND", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.NOT_FOUND)

        /** 资源冲突 / 重复 */
        fun conflict(code: String = "CONFLICT", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.CONFLICT)

        /** 内部错误 */
        fun internal(code: String = "INTERNAL_ERROR", message: String, cause: Throwable? = null): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.INTERNAL_SERVER_ERROR, cause)
    }
}
