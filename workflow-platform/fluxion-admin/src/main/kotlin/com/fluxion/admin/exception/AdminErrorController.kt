package com.fluxion.admin.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin 后台统一错误页面控制器。
 *
 * 捕获 Spring Boot 默认错误转发（如 404 无匹配处理器、500 内部错误等），
 * 将其转换为与 [GlobalExceptionHandler] 一致的 [AdminErrorResponse] 结构，
 * 确保前端收到的错误格式统一，便于 errorHandler 统一处理。
 */
@RestController
class AdminErrorController : ErrorController {

    @RequestMapping("/error")
    fun handleError(request: HttpServletRequest): ResponseEntity<AdminErrorResponse> {
        val statusCode = request.getAttribute("jakarta.servlet.error.status_code") as? Int
            ?: HttpStatus.INTERNAL_SERVER_ERROR.value()
        val path = request.getAttribute("jakarta.servlet.error.request_uri") as? String
            ?: request.requestURI
        val message = when (statusCode) {
            HttpStatus.NOT_FOUND.value() -> "接口不存在: $path"
            HttpStatus.FORBIDDEN.value() -> "无权限访问: $path"
            HttpStatus.UNAUTHORIZED.value() -> "未授权，请先登录"
            HttpStatus.BAD_REQUEST.value() -> "请求参数错误"
            HttpStatus.INTERNAL_SERVER_ERROR.value() -> "服务器内部错误，请联系管理员"
            else -> "请求处理失败"
        }
        val code = when (statusCode) {
            HttpStatus.NOT_FOUND.value() -> "NOT_FOUND"
            HttpStatus.FORBIDDEN.value() -> "ACCESS_DENIED"
            HttpStatus.UNAUTHORIZED.value() -> "UNAUTHORIZED"
            HttpStatus.BAD_REQUEST.value() -> "BAD_REQUEST"
            HttpStatus.INTERNAL_SERVER_ERROR.value() -> "INTERNAL_ERROR"
            else -> "ERROR"
        }
        return ResponseEntity.status(statusCode).body(AdminErrorResponse(code, message))
    }
}
