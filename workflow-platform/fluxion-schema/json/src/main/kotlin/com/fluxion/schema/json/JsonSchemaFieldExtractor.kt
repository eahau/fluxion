/**
 * Extracts the uniform [SchemaField] tree from a JSON Schema document.
 *
 * Handles the JSON Schema constructs that surface in workflow input/output
 * definitions: `properties`/`required` pairs, `items` for arrays, and local
 * `$ref` pointers against `#/definitions/...` or `#/properties/...`. A
 * mutable visited-refs set is threaded through recursive calls so cyclic
 * schemas terminate instead of overflowing the stack.
 */
package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.fluxion.schema.util.JsonUtil

/**
 * Walks the raw JSON Schema source text to produce a flattened [SchemaField]
 * list with nested children for OBJECT/ARRAY branches.
 */
class JsonSchemaFieldExtractor : SchemaFieldExtractor {

    @Suppress("UNCHECKED_CAST")
    override fun extractFields(schema: Schema): List<SchemaField> {
        val root = JsonUtil.deserialize(schema.raw, Map::class.java) as Map<String, Any>
        return extractProperties(root, root, "", mutableSetOf())
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractProperties(
        schemaNode: Map<String, Any>,
        root: Map<String, Any>,
        path: String,
        visitedRefs: MutableSet<String>
    ): List<SchemaField> {
        // Resolve top-level $ref first so that schemas like
        //   {"$ref":"#/definitions/Order"}
        // still produce a field tree rooted at the referenced definition.
        val resolved = resolveRef(schemaNode, root, visitedRefs) ?: schemaNode

        val properties = resolved["properties"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val required = resolved["required"] as? List<String> ?: emptyList()

        return properties.map { (name, prop) ->
            extractField(name, prop, root, path, required.contains(name), visitedRefs)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractField(
        name: String,
        prop: Map<String, Any>,
        root: Map<String, Any>,
        path: String,
        required: Boolean,
        visitedRefs: MutableSet<String>
    ): SchemaField {
        val currentPath = if (path.isEmpty()) name else "$path.$name"

        val resolvedProp = resolveRef(prop, root, visitedRefs) ?: prop

        val type = resolvedProp["type"] as? String ?: inferType(resolvedProp)
        val fieldType = mapJsonType(type)

        val nestedFields = when (fieldType) {
            FieldType.OBJECT -> extractProperties(resolvedProp, root, currentPath, visitedRefs)
            FieldType.ARRAY -> {
                // Wrap single-item schema in a synthetic `properties["*"]` so
                // the generic extractor can be reused for array elements.
                val items = resolvedProp["items"] as? Map<String, Any>
                items?.let {
                    val itemFields = extractProperties(
                        mapOf("properties" to mapOf("*" to it)),
                        root,
                        currentPath,
                        visitedRefs
                    )
                    itemFields
                } ?: emptyList()
            }
            else -> emptyList()
        }

        return SchemaField(
            name = name,
            type = fieldType,
            required = required,
            description = resolvedProp["description"] as? String,
            format = resolvedProp["format"] as? String,
            nestedFields = nestedFields,
            metadata = buildMetadata(prop)
        )
    }

    /**
     * Resolve a JSON Reference pointer (`#/...`) against the document root.
     *
     * Only local refs (starting with `#/`) are honoured; external URIs are
     * skipped and callers fall back to the unresolved node. The visited set
     * detects A→B→A style cycles; `finally` removes the marker so the same
     * ref can be safely used across different branches of the tree.
     *
     * @return resolved schema map, or `null` if the ref is external, cyclic,
     *         or points to a non-existent path.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveRef(
        node: Map<String, Any>,
        root: Map<String, Any>,
        visitedRefs: MutableSet<String>
    ): Map<String, Any>? {
        val ref = node["\$ref"] as? String ?: return null

        if (!ref.startsWith("#/")) return null

        if (ref in visitedRefs) return null
        visitedRefs.add(ref)

        return try {
            val segments = ref.removePrefix("#/").split('/')
            var current: Any = root
            for (segment in segments) {
                current = when (current) {
                    is Map<*, *> -> current[segment] ?: return null
                    else -> return null
                }
            }
            current as? Map<String, Any>
        } finally {
            visitedRefs.remove(ref)
        }
    }

    private fun inferType(prop: Map<String, Any>): String = when {
        prop.containsKey("properties") -> "object"
        prop.containsKey("items") -> "array"
        prop.containsKey("\$ref") -> "object"
        else -> "string"
    }

    private fun mapJsonType(type: String): FieldType = when (type) {
        "string" -> FieldType.STRING
        "integer" -> FieldType.INTEGER
        "number" -> FieldType.DOUBLE
        "boolean" -> FieldType.BOOLEAN
        "object" -> FieldType.OBJECT
        "array" -> FieldType.ARRAY
        "null" -> FieldType.NULL
        else -> FieldType.ANY
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildMetadata(prop: Map<String, Any>): Map<String, Any> {
        val keys = listOf("enum", "pattern", "minLength", "maxLength", "minimum", "maximum", "\$ref")
        return keys.mapNotNull { key ->
            prop[key]?.let { key to it }
        }.toMap()
    }
}
