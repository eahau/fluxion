package com.fluxion.admin.compat

import com.fluxion.admin.validator.SchemaCompatibilityResult
import com.fluxion.admin.validator.SchemaFieldChange
import org.slf4j.*

/**
 * Backwards-compatible wrapper around the new `SchemaCompatibilityValidator` class.
 *
 * Provides deprecated static-style helpers so existing callers still compile without
 * imports being changed. New call sites should instantiate `SchemaCompatibilityValidator`
 * directly.
 *
 * Each helper prints a one-time deprecation warning to help callers migrate.
 */
object SchemaCompatibility {

    private val log = LoggerFactory.getLogger(SchemaCompatibility::class.java)
    private var warned = false

    private val delegate = com.fluxion.admin.validator.SchemaCompatibilityValidator()

    /**
     * Delegates to `SchemaCompatibilityValidator.validate(..)`.
     *
     * @param oldJson serialized JSON Schema (null → treated as a fresh schema, all added)
     * @param newJson serialized JSON Schema (null → treated as a full deletion)
     */
    @JvmStatic
    fun validateCompatibility(oldJson: String?, newJson: String?): SchemaCompatibilityResult {
        warnDeprecation("validateCompatibility", "SchemaCompatibilityValidator#validate")
        return delegate.validate(oldJson, newJson)
    }

    /**
     * @param fieldChanges per-field diff list
     * @return count of changes tagged as `DELETE` or `MODIFY`; useful for simple UI badges.
     */
    @JvmStatic
    fun countBreakingChanges(fieldChanges: List<SchemaFieldChange>): Int {
        warnDeprecation("countBreakingChanges", "direct List.filter usage inline")
        return fieldChanges.count { it.changeType == "DELETE" || it.changeType == "MODIFY" }
    }

    /**
     * @param result validator result bean
     * @return violations converted into name/level/message triples for UI display.
     */
    @JvmStatic
    fun toViolationRecords(result: SchemaCompatibilityResult, schemaId: Long?): List<Triple<Long?, String, String>> {
        warnDeprecation("toViolationRecords", "map violations in caller service")
        return result.violations.mapIndexed { idx, msg ->
            Triple(schemaId, "ERROR", "COMPAT_${idx + 1}: $msg")
        }
    }

    private fun warnDeprecation(method: String, replacement: String) {
        if (!warned) {
            warned = true
            log.warn { "Deprecated call SchemaCompatibility.$method; switch to $replacement instead." }
        }
    }
}
