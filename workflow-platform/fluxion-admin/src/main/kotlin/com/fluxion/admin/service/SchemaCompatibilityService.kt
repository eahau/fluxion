package com.fluxion.admin.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.slf4j.*
import org.springframework.stereotype.Service

/**
 * Schema compatibility validator + payload-field mapping engine.
 *
 * Drives the two "function-set composability" guarantees the user specified:
 *
 * 1. **Target-subset rule**: function set `B` can be composed *after* `A` iff
 *    `B.input.required ⊆ A.output` and every required field's JSON-Schema type
 *    is assignment-compatible (int ↔ number is allowed but number → int warns).
 *    Additional fields that `A.output` carries but `B.input` does not declare
 *    are dropped on the floor by the mapping layer (or passed via `_extra`).
 *
 * 2. **Automatic field mapping layer**: when the strict subset rule fails, the
 *    user provides a **mapping JSON object** like:
 *    ```json
 *    { "targetField1": "payload.srcField", "targetField2": "$.nodeOutputs.last.amount" }
 *    ```
 *    This service then performs a "post-mapping subset check" so the designer
 *    can preview compatibility *before* publishing a DAG.
 *
 * ### Mapping DSL (kept deliberately tiny, no expressions)
 *
 * | RHS syntax       | Meaning                                                        |
 * |------------------|----------------------------------------------------------------|
 * | `"payload.x"`    | Top-level input payload field `x` (used for set-invocation).   |
 * | `"$.x.y"`        | JSON-Path style (only dot-notation supported, no filters).     |
 * | literal `123.45` | Booleans / numbers / strings / null are copied literally.      |
 *
 * No scripting support by design — user-side composition is declarative only.
 */
@Service
class SchemaCompatibilityService(
    private val om: ObjectMapper = ObjectMapper().findAndRegisterModules()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate that `targetSchema` is a field-wise compatible subset of
     * `sourceSchema` (without any user-provided mapping layer).
     */
    fun checkSubset(sourceSchemaJson: String?, targetSchemaJson: String?): CompatResult =
        checkSubset(parseOrEmpty(sourceSchemaJson), parseOrEmpty(targetSchemaJson), emptyMap())

    /**
     * Validate that `targetSchema` is a field-wise compatible subset of the
     * *virtual* schema you would obtain if `sourceSchemaJson` were projected
     * through `mappingJson` first. Returns missing/type-mismatch diagnostics
     * on the TARGET namespace so designer can highlight the failing input
     * form fields directly.
     */
    fun checkWithMapping(
        sourceSchemaJson: String?,
        targetSchemaJson: String?,
        mappingJson: String?
    ): CompatResult {
        val src = parseOrEmpty(sourceSchemaJson)
        val tgt = parseOrEmpty(targetSchemaJson)
        val mapping = parseMapping(mappingJson)
        return checkSubset(src, tgt, mapping)
    }

    /**
     * Apply a mapping object to an actual runtime payload map. Safe — unknown
     * target fields are skipped, missing source paths produce `null` (and are
     * left up to downstream schema validation).
     */
    fun applyMapping(source: Map<String, Any?>, mappingJson: String?): Map<String, Any?> {
        val mapping = parseMapping(mappingJson)
        if (mapping.isEmpty()) return source
        val out = LinkedHashMap<String, Any?>()
        mapping.forEach { (tgtKey, rhs) ->
            out[tgtKey] = evalRhs(rhs, source)
        }
        // Merge unmapped source extras only when mapping wasn't a complete rewrite.
        val mappedEverything = mapping.keys.containsAll(source.keys)
        if (!mappedEverything) {
            source.forEach { (k, v) -> if (k !in out) out["_extra_$k"] = v }
        }
        return out
    }

    /** Convenience entry: apply mapping given a serialized JSON source payload. */
    fun applyMappingJson(sourcePayloadJson: String, mappingJson: String?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val src = om.readValue(sourcePayloadJson, Map::class.java) as Map<String, Any?>
        return applyMapping(src, mappingJson)
    }

    // ═══════════════════════════════════════════════════════════════
    // Core subset check
    // ═══════════════════════════════════════════════════════════════

    private fun checkSubset(
        src: ParsedSchema,
        tgt: ParsedSchema,
        mapping: Map<String, MappingRhs>
    ): CompatResult {
        if (tgt.properties.isEmpty()) return CompatResult.OK_EMPTY

        val projectedSrcTypes: Map<String, Set<String>> =
            if (mapping.isEmpty()) src.properties.mapValues { (_, v) -> v.jsonTypes }
            else mapping.mapValues { (_, rhs) -> resolveProjectedType(rhs, src) }

        val missing = mutableListOf<String>()
        val typeMismatch = mutableListOf<TypeMismatch>()
        val warnings = mutableListOf<String>()

        tgt.required.forEach { reqKey ->
            val srcTypes = projectedSrcTypes[reqKey]
            if (srcTypes == null) {
                // When mapping is present we only consider keys the user explicitly projected.
                missing += reqKey
                return@forEach
            }
            val tgtTypes = tgt.properties[reqKey]?.jsonTypes ?: emptySet()
            if (!typesCompatible(srcTypes, tgtTypes)) {
                typeMismatch += TypeMismatch(
                    field = reqKey,
                    sourceTypes = srcTypes,
                    targetTypes = tgtTypes
                )
            } else if (typesCoerced(srcTypes, tgtTypes)) {
                warnings += "Field '$reqKey' coerces $srcTypes → $tgtTypes (precision loss possible)"
            }
        }

        val ok = missing.isEmpty() && typeMismatch.isEmpty()
        return CompatResult(
            ok = ok,
            requiredFieldsMissing = missing,
            typeMismatches = typeMismatch,
            warnings = warnings,
            resolvedSourceProjectedTypes = projectedSrcTypes
        )
    }

    private fun typesCompatible(src: Set<String>, tgt: Set<String>): Boolean {
        if (tgt.isEmpty() || src.isEmpty()) return true         // no type info = trust the user
        // Any target type covered by any source type → OK.
        return tgt.all { tt -> src.any { st -> assignable(st, tt) } }
    }

    private fun assignable(srcType: String, tgtType: String): Boolean {
        if (srcType == tgtType) return true
        // JSON-Schema number ↔ integer: integer is a valid source for number (not vice-versa without warning).
        if (srcType == "integer" && tgtType == "number") return true
        // Everything can be a string at the cost of serialization format.
        if (tgtType == "string") return true
        return false
    }

    private fun typesCoerced(src: Set<String>, tgt: Set<String>): Boolean {
        // Warning case 1: number (source) → integer (target) loses fractional part.
        if ("number" in src && "integer" in tgt && "integer" !in src) return true
        if (src.isNotEmpty() && tgt == setOf("string") && src != setOf("string")) return true
        return false
    }

    // ═══════════════════════════════════════════════════════════════
    // Mapping DSL evaluator (RHS = string path / literal)
    // ═══════════════════════════════════════════════════════════════

    private fun evalRhs(rhs: MappingRhs, source: Map<String, Any?>): Any? = when (rhs) {
        is MappingRhs.PathRhs    -> walk(source, rhs.segments)
        is MappingRhs.LiteralRhs -> rhs.value
    }

    private fun resolveProjectedType(rhs: MappingRhs, src: ParsedSchema): Set<String> = when (rhs) {
        is MappingRhs.LiteralRhs -> typeOfLiteral(rhs.value)
        is MappingRhs.PathRhs    -> walkSchemaType(src, rhs.segments)
    }

    private tailrec fun walk(current: Any?, segments: List<String>): Any? {
        if (segments.isEmpty()) return current
        val head = segments.first()
        val rest = segments.drop(1)
        return when (current) {
            is Map<*, *> -> walk(current[head], rest)
            else         -> null
        }
    }

    private tailrec fun walkSchemaType(current: ParsedSchema, segments: List<String>): Set<String> {
        if (segments.isEmpty()) return emptySet()
        val head = segments.first()
        val rest = segments.drop(1)
        val node = current.properties[head] ?: return emptySet()
        return if (rest.isEmpty()) node.jsonTypes else walkSchemaType(node.nestedSchema, rest)
    }

    private fun typeOfLiteral(value: Any?): Set<String> = when (value) {
        null          -> setOf("null")
        is Boolean    -> setOf("boolean")
        is Int, Long  -> setOf("integer")
        is Number     -> setOf("number")
        is String     -> setOf("string")
        is List<*>    -> setOf("array")
        is Map<*, *>  -> setOf("object")
        else          -> emptySet()
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON-Schema parsing (best-effort, tolerant to external edge-cases)
    // ═══════════════════════════════════════════════════════════════

    private fun parseOrEmpty(json: String?): ParsedSchema {
        if (json.isNullOrBlank()) return ParsedSchema.EMPTY
        return try {
            val root = om.readTree(json)
            parseNode(root)
        } catch (t: Throwable) {
            log.warn(t) { "Failed to parse JSON-Schema, treating as EMPTY" }
            ParsedSchema.EMPTY
        }
    }

    private fun parseNode(node: JsonNode): ParsedSchema {
        if (!node.isObject) return ParsedSchema.EMPTY
        val props = node["properties"]?.takeIf { it.isObject } as? ObjectNode
        val required = node["required"]?.takeIf { it.isArray }
            ?.mapNotNull { it.textValue() }?.toList() ?: emptyList()

        val types = readTypes(node)
        val parsedProps = LinkedHashMap<String, ParsedProperty>()
        props?.fields()?.forEach { (name, value) ->
            if (value.isObject) {
                val nested = if (value.has("properties") || value.has("items")) parseNode(value) else ParsedSchema.EMPTY
                parsedProps[name] = ParsedProperty(
                    jsonTypes = readTypes(value),
                    nestedSchema = nested
                )
            }
        }
        return ParsedSchema(
            jsonTypes = types,
            properties = parsedProps,
            required = required.toSet()
        )
    }

    private fun readTypes(node: JsonNode): Set<String> {
        val t = node["type"] ?: return emptySet()
        return when {
            t.isTextual -> setOf(t.textValue())
            t.isArray   -> t.mapNotNull { it.textValue() }.toSet()
            else        -> emptySet()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Mapping JSON parsing
    // ═══════════════════════════════════════════════════════════════

    private fun parseMapping(mappingJson: String?): Map<String, MappingRhs> {
        if (mappingJson.isNullOrBlank()) return emptyMap()
        val node = try { om.readTree(mappingJson) } catch (t: Throwable) { return emptyMap() }
        if (!node.isObject) return emptyMap()
        val out = LinkedHashMap<String, MappingRhs>()
        node.fields().forEach { (k, v) ->
            out[k] = when {
                v.isTextual -> parsePath(v.textValue())
                else        -> MappingRhs.LiteralRhs(om.treeToValue(v, Any::class.java))
            }
        }
        return out
    }

    private fun parsePath(raw: String): MappingRhs {
        val s = raw.trim()
        val head = s.removePrefix("$.")
            .removePrefix("payload.")
            .removePrefix("inputs.")
            .removePrefix("nodeOutputs.")
            .trim()
        if (head.isEmpty()) return MappingRhs.LiteralRhs(raw)
        val segments = head.split(".").filter { it.isNotBlank() }
        return if (segments.isEmpty()) MappingRhs.LiteralRhs(raw) else MappingRhs.PathRhs(segments)
    }

    // ═══════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════

    private data class ParsedSchema(
        val jsonTypes: Set<String> = emptySet(),
        val properties: Map<String, ParsedProperty> = emptyMap(),
        val required: Set<String> = emptySet()
    ) {
        companion object {
            val EMPTY = ParsedSchema()
        }
    }

    private data class ParsedProperty(
        val jsonTypes: Set<String>,
        val nestedSchema: ParsedSchema
    )

    private sealed interface MappingRhs {
        data class PathRhs(val segments: List<String>) : MappingRhs
        data class LiteralRhs(val value: Any?) : MappingRhs
    }
}

/**
 * Result of a compatibility check. `ok = true` means the designer can safely
 * wire the two endpoints together. When false, the UI renders:
 *   - `requiredFieldsMissing` in red under the target schema section
 *   - `typeMismatches` in yellow next to the offending field
 *   - `warnings` as an inline info banner (won't block save).
 */
data class CompatResult(
    val ok: Boolean,
    val requiredFieldsMissing: List<String>,
    val typeMismatches: List<TypeMismatch>,
    val warnings: List<String>,
    val resolvedSourceProjectedTypes: Map<String, Set<String>>
) {
    companion object {
        val OK_EMPTY = CompatResult(
            ok = true, requiredFieldsMissing = emptyList(),
            typeMismatches = emptyList(), warnings = emptyList(),
            resolvedSourceProjectedTypes = emptyMap()
        )
    }
}

data class TypeMismatch(
    val field: String,
    val sourceTypes: Set<String>,
    val targetTypes: Set<String>
)
