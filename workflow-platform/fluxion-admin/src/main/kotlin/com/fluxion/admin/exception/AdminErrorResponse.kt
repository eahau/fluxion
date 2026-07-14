package com.fluxion.admin.exception

/**
 * Unified error DTO returned by every admin exception path.
 *
 * Both `GlobalExceptionHandler` and the servlet error fallback `AdminErrorController`
 * convert caught exceptions into this structure so the frontend always sees a single,
 * machine-parseable envelope.
 *
 * @property code stable short error key used by frontend routing and i18n lookups.
 * @property message human-readable display text (already localized on the server).
 * @property detail optional per-field or extra diagnostic details (e.g. validation errors).
 * @property timestamp wall-clock millis of when the response was assembled.
 */
data class AdminErrorResponse(
    val code: String,
    val message: String,
    val detail: Map<String, String>? = null,
    val timestamp: Long = System.currentTimeMillis()
)
