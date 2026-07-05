package com.fluxion.admin.exception

import com.fluxion.core.exception.WorkflowException
import com.fluxion.admin.security.TenantAccessDeniedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException

/**
 * Admin 后台全局异常处理器。
 *
 * 统一捕获 Controller 层抛出的异常，屏蔽内部堆栈，向前端返回结构化的 [AdminErrorResponse]。
 * 这样前后端异常格式保持一致，前端 errorHandler 可以统一读取 [code] 与 [message]。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(WorkflowAdminException::class)
    fun handleAdminException(ex: WorkflowAdminException): ResponseEntity<AdminErrorResponse> {
        log.warn("Admin business error [{}]: {}", ex.errorCode, ex.message)
        return ResponseEntity
            .status(ex.httpStatus)
            .body(AdminErrorResponse(ex.errorCode, ex.message ?: "业务异常"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<AdminErrorResponse> {
        log.warn("Illegal argument: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(AdminErrorResponse("ILLEGAL_ARGUMENT", ex.message ?: "请求参数错误"))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(ex: NoSuchElementException): ResponseEntity<AdminErrorResponse> {
        log.warn("Resource not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(AdminErrorResponse("NOT_FOUND", ex.message ?: "资源不存在"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<AdminErrorResponse> {
        log.warn("Access denied: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(AdminErrorResponse("ACCESS_DENIED", ex.message ?: "无权限访问"))
    }

    @ExceptionHandler(TenantAccessDeniedException::class)
    fun handleTenantAccessDenied(ex: TenantAccessDeniedException): ResponseEntity<AdminErrorResponse> {
        log.warn("Tenant access denied: user={}, appGroup={}", ex.username, ex.appGroup)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(AdminErrorResponse("TENANT_ACCESS_DENIED", "无权访问应用分组 [${ex.appGroup}]"))
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<AdminErrorResponse> {
        log.warn("Bad credentials: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(AdminErrorResponse("BAD_CREDENTIALS", ex.message ?: "用户名或密码错误"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<AdminErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}${it.defaultMessage?.let { msg -> ": $msg" } ?: ""}" }
            .takeIf { it.isNotBlank() } ?: "请求参数校验失败"
        log.warn("Validation failed: {}", message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(AdminErrorResponse("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFound(ex: NoHandlerFoundException): ResponseEntity<AdminErrorResponse> {
        log.warn("No handler found: {} {}", ex.httpMethod, ex.requestURL)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .header("X-Execution-Id", "")
            .body(AdminErrorResponse("NOT_FOUND", "接口不存在: ${ex.httpMethod} ${ex.requestURL}"))
    }

    @ExceptionHandler(WorkflowException::class)
    fun handleWorkflowException(ex: WorkflowException): ResponseEntity<AdminErrorResponse> {
        log.warn("Workflow error [{}]: {}", ex.errorCode, ex.message)
        val status = when {
            ex.errorCode.startsWith("WF-VALIDATION") -> HttpStatus.BAD_REQUEST
            ex.errorCode.startsWith("WF-REGISTRY")   -> HttpStatus.NOT_FOUND
            ex.errorCode.startsWith("WF-DECORATOR")  -> HttpStatus.TOO_MANY_REQUESTS
            else                                      -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        return ResponseEntity
            .status(status)
            .header("X-Execution-Id", "")
            .body(AdminErrorResponse(ex.errorCode, ex.message ?: "工作流执行失败"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<AdminErrorResponse> {
        log.error("Unexpected admin error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header("X-Execution-Id", "")
            .body(AdminErrorResponse("INTERNAL_ERROR", "服务器内部错误，请联系管理员"))
    }
}
