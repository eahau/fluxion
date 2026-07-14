package com.fluxion.admin.exception

import org.springframework.http.HttpStatus

/**
 * Unified domain/business exception for the admin backend.
 *
 * Callers use [errorCode] to identify the machine-readable error class and [httpStatus]
 * to decide the HTTP response status that [GlobalExceptionHandler] will emit. The
 * runtime [message] is preserved as a human-readable surface for frontend display and
 * is not guaranteed to be stable across releases.
 */
class WorkflowAdminException(
    val errorCode: String,
    message: String,
    val httpStatus: HttpStatus = HttpStatus.BAD_REQUEST,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /** Generic 4xx input / validation failure. */
        fun badRequest(code: String = "BAD_REQUEST", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.BAD_REQUEST)

        /** 404 requested resource does not exist in this tenant scope. */
        fun notFound(code: String = "NOT_FOUND", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.NOT_FOUND)

        /** 409 resource already exists / optimistic-lock or unique-index conflict. */
        fun conflict(code: String = "CONFLICT", message: String): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.CONFLICT)

        /** 500 internal or upstream-integration failure; optionally attaches a root cause. */
        fun internal(code: String = "INTERNAL_ERROR", message: String, cause: Throwable? = null): WorkflowAdminException =
            WorkflowAdminException(code, message, HttpStatus.INTERNAL_SERVER_ERROR, cause)
    }
}
