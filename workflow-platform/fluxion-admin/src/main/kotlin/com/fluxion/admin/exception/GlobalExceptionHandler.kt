package com.fluxion.admin.exception

import com.fasterxml.jackson.databind.JsonMappingException
import org.slf4j.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.persistence.EntityNotFoundException

/**
 * Servlet `@RestControllerAdvice` that maps common domain/framework exceptions to
 * OpenAPI-compliant `AdminErrorResponse` DTOs with the correct HTTP status.
 *
 * All handlers log the underlying exception at warn/error level and include a
 * stable machine-readable `code` plus a human-readable `message`.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Business/domain exceptions thrown by `*Service` beans carry a suggested HTTP
     * status that is used verbatim on the response.
     */
    @ExceptionHandler(WorkflowAdminException::class)
    fun handleWorkflowAdminException(ex: WorkflowAdminException): ResponseEntity<AdminErrorResponse> {
        log.warn { "Business exception: ${ex.errorCode} / ${ex.message}" }
        return ResponseEntity.status(ex.httpStatus)
            .body(AdminErrorResponse(ex.errorCode, ex.message ?: "Unknown admin error"))
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException): ResponseEntity<AdminErrorResponse> {
        log.warn { "404 not found: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(AdminErrorResponse("NOT_FOUND", ex.message ?: "Resource not found"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<AdminErrorResponse> {
        log.warn { "403 access denied: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(AdminErrorResponse("FORBIDDEN", ex.message ?: "Access denied"))
    }

    /**
     * Catches both `@Valid` violations from `@RequestBody` binding and JSR-303
     * violations propagated from service-layer manual validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<AdminErrorResponse> {
        val details = ex.bindingResult.fieldErrors.associate { fe: FieldError ->
            fe.field to (fe.defaultMessage ?: "Invalid value")
        }
        log.warn { "422 validation error for ${ex.parameter.parameterName}: $details" }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(AdminErrorResponse("VALIDATION", "Request validation failed", HashMap(details)))
    }

    @ExceptionHandler(BindException::class)
    fun handleBind(ex: BindException): ResponseEntity<AdminErrorResponse> {
        val details = ex.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
        log.warn { "400 bind error: $details" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AdminErrorResponse("BAD_REQUEST", "Unable to bind request parameters", HashMap(details)))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadJson(ex: HttpMessageNotReadableException): ResponseEntity<AdminErrorResponse> {
        val cause = ex.cause as? JsonMappingException
        val msg = cause?.path?.joinToString(".") { it.fieldName ?: "?" }?.let { path ->
            "Invalid JSON at field '$path'"
        } ?: (ex.message ?: "Malformed request body")
        log.warn { "400 bad JSON: $msg" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AdminErrorResponse("BAD_REQUEST", msg))
    }

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleIllegalState(ex: RuntimeException): ResponseEntity<AdminErrorResponse> {
        log.warn { "400 illegal state: ${ex.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AdminErrorResponse("BAD_REQUEST", ex.message ?: "Illegal argument"))
    }

    /**
     * Unhandled exceptions: log stack trace at error level but never leak internals
     * to clients (message is intentionally generic).
     */
    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception): ResponseEntity<AdminErrorResponse> {
        log.error(ex) { "Unexpected server error" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(AdminErrorResponse("INTERNAL", "Internal server error"))
    }
}
