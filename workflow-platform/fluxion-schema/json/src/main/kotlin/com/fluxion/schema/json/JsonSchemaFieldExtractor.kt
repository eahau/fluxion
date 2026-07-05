package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaFieldExtractor
import com.fluxion.schema.model.FieldType
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaField
import com.fluxion.schema.util.JsonUtil

/**
 * 从 JSON Schema 提取统一的 [SchemaField] 字段树。
 *
 * 支持：
 * - properties / required 字段提取
 * - array items 提取
 * - `$ref` 内部引用解析（`#/definitions/xxx`、`#/properties/xxx`）
 * - 循环引用检测（避免无限递归）
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
        // 处理 $ref：如果当前节点本身就是一个引用，先解析引用
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

        // 解析 $ref
        val resolvedProp = resolveRef(prop, root, visitedRefs) ?: prop

        val type = resolvedProp["type"] as? String ?: inferType(resolvedProp)
        val fieldType = mapJsonType(type)

        val nestedFields = when (fieldType) {
            FieldType.OBJECT -> extractProperties(resolvedProp, root, currentPath, visitedRefs)
            FieldType.ARRAY -> {
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
            metadata = buildMetadata(prop)  // metadata 保留原始 prop，包含 $ref 信息
        )
    }

    /**
     * 解析 $ref 引用。
     *
     * 支持：
     * - `#/definitions/User` → 从根节点的 definitions 中查找
     * - `#/properties/address` → 从根节点的 properties 中查找
     *
     * 循环引用检测：如果引用路径已在 visitedRefs 中，返回 null（避免无限递归）。
     *
     * @return 解析后的 schema 节点，无法解析或循环引用时返回 null
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveRef(
        node: Map<String, Any>,
        root: Map<String, Any>,
        visitedRefs: MutableSet<String>
    ): Map<String, Any>? {
        val ref = node["\$ref"] as? String ?: return null

        // 只处理内部引用（#/ 开头）
        if (!ref.startsWith("#/")) return null

        // 循环引用检测
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
