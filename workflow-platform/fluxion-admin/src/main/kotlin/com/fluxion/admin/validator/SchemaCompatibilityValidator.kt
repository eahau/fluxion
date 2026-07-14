package com.fluxion.admin.validator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory

/**
 * Aggregate result returned by `SchemaCompatibilityValidator`.
 *
 * @property violations machine-readable list of incompatibility messages; empty means both
 *                       schemas are forward/backward compatible.
 * @property fieldChanges human-readable per-field diff (add/delete/modify/rename) that the
 *                        UI can surface before an admin saves a schema edit.
 */
data class SchemaCompatibilityResult(
    val violations: List<String> = emptyList(),
    val fieldChanges: List<SchemaFieldChange> = emptyList()
) {
    val compatible: Boolean get() = violations.isEmpty()
}

/**
 * Single field-level change entry reported by the compatibility validator.
 */
data class SchemaFieldChange(
    val fieldPath: String,
    val changeType: String,
    val oldType: String? = null,
    val newType: String? = null,
    val oldRequired: Boolean? = null,
    val newRequired: Boolean? = null,
    val reason: String? = null
) {
    val description: String get() {
        return when (changeType) {
            "ADD" -> "Field added: $fieldPath"
            "DELETE" -> "Field deleted: $fieldPath"
            "MODIFY" -> "Field modified: $fieldPath type ${oldType ?: "?"} -> ${newType ?: "?"}"
            "RENAME" -> reason ?: "Field renamed: $fieldPath"
            else -> reason ?: changeType
        }
    }
}

/**
 * Validates forward/backward compatibility between two JSON Schema documents.
 *
 * Detects:
 *   1. Deleted required fields (breaking).
 *   2. Newly required fields added without defaults (breaking).
 *   3. Incompatible type changes (e.g. `string` → `integer`) without a converter.
 *   4. Removed `enum` values (consumers may fail validation).
 *   5. Tightened constraints (minimum/maximum/minLength/maxLength/new pattern).
 *
 * Used by the schema editor UI to warn administrators before they save a schema revision
 * that could break existing callers.
 */
class SchemaCompatibilityValidator {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper()

    companion object {
        private const val PATH_SEP = "/"

        private const val REASON_FIELD_DELETED = "Field removed - breaking change for consumers"
        private const val REASON_FIELD_RENAMED = "Field likely renamed - requires migration"
        private const val REASON_TYPE_CHANGED = "Field type changed - incompatible without conversion"
        private const val REASON_ENUM_REMOVED = "Enum value(s) removed - consumers may fail"
        private const val REASON_CONSTRAINT_TIGHTENED = "Constraints tightened - may reject previously valid data"
        private const val REASON_REQUIRED_ADDED = "Newly required field - consumers without it will fail"
    }

    /**
     * Compare two serialized JSON Schemas and return a compatibility report.
     *
     * Either input may be `null`: a null old document is treated as a fresh schema (all
     * fields added, no violations) and a null new document as a full deletion (all
     * fields deleted).
     */
    fun validate(oldJson: String?, newJson: String?): SchemaCompatibilityResult {
        if (oldJson == null && newJson == null) return SchemaCompatibilityResult()
        if (oldJson == null) {
            return SchemaCompatibilityResult(fieldChanges = extractFields(newJson!!).map {
                SchemaFieldChange(
                    fieldPath = it.key,
                    changeType = "ADD",
                    newType = it.value.type.firstOrNull(),
                    newRequired = it.value.required
                )
            })
        }
        if (newJson == null) {
            return SchemaCompatibilityResult(fieldChanges = extractFields(oldJson).map {
                SchemaFieldChange(
                    fieldPath = it.key,
                    changeType = "DELETE",
                    oldType = it.value.type.firstOrNull(),
                    oldRequired = it.value.required
                )
            })
        }

        val oldRoot = try { mapper.readTree(oldJson) } catch (e: Exception) { null }
        val newRoot = try { mapper.readTree(newJson) } catch (e: Exception) { null }
        if (oldRoot == null || newRoot == null) {
            return SchemaCompatibilityResult(
                violations = listOf("Failed to parse one or both JSON Schemas for compatibility check"),
                fieldChanges = emptyList()
            )
        }

        val oldFields = collectSchemaFields(oldRoot, "")
        val newFields = collectSchemaFields(newRoot, "")
        val violations = mutableListOf<String>()
        val fieldChanges = mutableListOf<SchemaFieldChange>()

        val deletedFields = oldFields.keys - newFields.keys
        deletedFields.forEach { path ->
            violations.add("$REASON_FIELD_DELETED: $path")
            fieldChanges.add(
                SchemaFieldChange(
                    fieldPath = path,
                    changeType = "DELETE",
                    oldType = oldFields[path]?.type?.firstOrNull(),
                    oldRequired = oldFields[path]?.required,
                    reason = REASON_FIELD_DELETED
                )
            )
        }

        val addedFields = newFields.keys - oldFields.keys
        addedFields.forEach { path ->
            val newField = newFields[path]!!
            if (newField.required) {
                violations.add("$REASON_REQUIRED_ADDED: $path")
            }
            fieldChanges.add(
                SchemaFieldChange(
                    fieldPath = path,
                    changeType = "ADD",
                    newType = newField.type.firstOrNull(),
                    newRequired = newField.required,
                    reason = if (newField.required) REASON_REQUIRED_ADDED else null
                )
            )
        }

        val commonFields = oldFields.keys.intersect(newFields.keys)
        commonFields.forEach { path ->
            val oldField = oldFields[path]!!
            val newField = newFields[path]!!
            val fieldViolations = checkFieldCompatibility(path, oldField, newField)
            violations.addAll(fieldViolations)
            if (oldField != newField) {
                fieldChanges.add(
                    SchemaFieldChange(
                        fieldPath = path,
                        changeType = "MODIFY",
                        oldType = oldField.type.firstOrNull(),
                        newType = newField.type.firstOrNull(),
                        oldRequired = oldField.required,
                        newRequired = newField.required,
                        reason = fieldViolations.firstOrNull()
                    )
                )
            }
        }

        return SchemaCompatibilityResult(
            violations = violations,
            fieldChanges = fieldChanges
        )
    }

    private fun collectSchemaFields(node: JsonNode, parentPath: String): Map<String, SchemaFieldInfo> {
        val result = mutableMapOf<String, SchemaFieldInfo>()
        val properties = node.get("properties") as? ObjectNode ?: return result
        val required = node.get("required")?.map { it.asText() }?.toSet() ?: emptySet()

        properties.fields().forEach { (fieldName, fieldNode) ->
            val fullPath = if (parentPath.isEmpty()) fieldName else "$parentPath.$fieldName"
            val types = collectTypes(fieldNode)
            result[fullPath] = SchemaFieldInfo(
                type = types,
                required = fieldName in required,
                rawNode = fieldNode
            )
            if (types.contains("object")) {
                result.putAll(collectSchemaFields(fieldNode, fullPath))
            }
            if (types.contains("array")) {
                fieldNode.get("items")?.let { itemsNode ->
                    if (itemsNode.has("properties")) {
                        result.putAll(collectSchemaFields(itemsNode, "$fullPath.items"))
                    }
                }
            }
        }
        return result
    }

    private fun collectTypes(node: JsonNode): Set<String> {
        val typeNode = node.get("type")
        return when {
            typeNode?.isArray == true -> typeNode.map { it.asText() }.toSet()
            typeNode?.isTextual == true -> setOf(typeNode.asText())
            else -> emptySet()
        }
    }

    private fun checkFieldCompatibility(
        path: String, oldField: SchemaFieldInfo, newField: SchemaFieldInfo
    ): List<String> {
        val violations = mutableListOf<String>()
        val oldNode = oldField.rawNode
        val newNode = newField.rawNode

        if (oldField.type != newField.type) {
            violations.add(
                "$REASON_TYPE_CHANGED: $path (${oldField.type.joinToString(", ")} -> ${newField.type.joinToString(", ")})"
            )
        }

        if (!oldField.required && newField.required) {
            violations.add("$REASON_REQUIRED_ADDED: $path")
        }

        val oldEnum = collectEnumValues(oldNode)
        val newEnum = collectEnumValues(newNode)
        if (oldEnum.isNotEmpty() && newEnum.isNotEmpty()) {
            val removedEnum = oldEnum - newEnum
            if (removedEnum.isNotEmpty()) {
                violations.add(
                    "$REASON_ENUM_REMOVED: $path (removed: ${removedEnum.joinToString(", ")})"
                )
            }
        } else if (oldEnum.isNotEmpty() && newEnum.isEmpty()) {
            violations.add(
                "$REASON_ENUM_REMOVED: $path (enum constraint was removed, widening - safe)"
            )
        }

        val tightenedConstraints = checkConstraintTightening(path, oldNode, newNode)
        violations.addAll(tightenedConstraints)

        return violations
    }

    private fun collectEnumValues(node: JsonNode): Set<String> {
        val enumNode = node.get("enum") ?: return emptySet()
        return enumNode.map { it.asText() }.toSet()
    }

    private fun checkConstraintTightening(path: String, oldNode: JsonNode, newNode: JsonNode): List<String> {
        val violations = mutableListOf<String>()

        val oldMin = oldNode.get("minimum")?.asDouble()
        val newMin = newNode.get("minimum")?.asDouble()
        if (oldMin != null && newMin != null && newMin > oldMin) {
            violations.add("$REASON_CONSTRAINT_TIGHTENED: $path minimum $oldMin -> $newMin")
        }
        val oldMax = oldNode.get("maximum")?.asDouble()
        val newMax = newNode.get("maximum")?.asDouble()
        if (oldMax != null && newMax != null && newMax < oldMax) {
            violations.add("$REASON_CONSTRAINT_TIGHTENED: $path maximum $oldMax -> $newMax")
        }

        val oldMinLen = oldNode.get("minLength")?.asInt()
        val newMinLen = newNode.get("minLength")?.asInt()
        if (oldMinLen != null && newMinLen != null && newMinLen > oldMinLen) {
            violations.add("$REASON_CONSTRAINT_TIGHTENED: $path minLength $oldMinLen -> $newMinLen")
        }
        val oldMaxLen = oldNode.get("maxLength")?.asInt()
        val newMaxLen = newNode.get("maxLength")?.asInt()
        if (oldMaxLen != null && newMaxLen != null && newMaxLen < oldMaxLen) {
            violations.add("$REASON_CONSTRAINT_TIGHTENED: $path maxLength $oldMaxLen -> $newMaxLen")
        }

        val oldPattern = oldNode.get("pattern")?.asText()
        val newPattern = newNode.get("pattern")?.asText()
        if (oldPattern == null && newPattern != null) {
            violations.add("$REASON_CONSTRAINT_TIGHTENED: $path new pattern constraint added")
        }

        return violations
    }

    private fun extractFields(json: String): Map<String, SchemaFieldInfo> {
        val root = try { mapper.readTree(json) } catch (e: Exception) { return emptyMap() }
        return collectSchemaFields(root, "")
    }

    private data class SchemaFieldInfo(
        val type: Set<String>,
        val required: Boolean,
        val rawNode: JsonNode
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SchemaFieldInfo) return false
            return type == other.type && required == other.required
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + required.hashCode()
            return result
        }
    }
}
