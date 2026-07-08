package com.fluxion.admin.exception

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Fallback servlet error controller for the admin application.
 *
 * Intercepts default Spring Boot error dispatches (e.g. 404 for unmapped handlers, 500
 * from filters, or errors that escape the MVC layer) and normalizes them into the same
 * [AdminErrorResponse] envelope that [GlobalExceptionHandler] produces. This keeps the
 * frontend `errorHandler` unaware of whether an error originated inside a controller or
 * somewhere earlier/later in the filter chain.
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
            HttpStatus.NOT_FOUND.value() -> "Endpoint not found: $path"
            HttpStatus.FORBIDDEN.value() -> "Access denied: $path"
            HttpStatus.UNAUTHORIZED.value() -> "Unauthorized, please login first"
            HttpStatus.BAD_REQUEST.value() -> "Invalid request parameters"
            HttpStatus.INTERNAL_SERVER_ERROR.value() -> "Internal server error, please contact administrator"
            else -> "Request processing failed"
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
