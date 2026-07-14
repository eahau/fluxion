package com.fluxion.schema.model

/**
 * Pointer to a registered, named schema — used for cross-schema references
 * and runtime lookup via [SchemaResolver].
 *
 * Wire format is `<format-code>:<name>` (colon-separated), matching the
 * convention used in workflow DSLs and admin APIs:
 * ```
 * json-schema:User       → JSON Schema named "User"
 * protobuf:com.foo.User  → Protobuf message "com.foo.User"
 * avro:OrderRecord       → Avro record "OrderRecord"
 * ```
 *
 * An optional numeric `version` field is reserved for future use by
 * registries that support versioned schemas (the core `InMemorySchemaRegistry`
 * currently stores only the latest version).
 */
data class SchemaRef(
    val format: SchemaFormat,
    val name: String,
    val version: Long? = null
) {
    companion object {

        /**
         * Parses a `<format>:<name>` reference string.
         *
         * Returns `null` (instead of throwing) on malformed input because
         * references often come from user-authored workflow configs where
         * graceful degradation is preferred. Format codes are resolved via
         * [SchemaFormat.fromCodeOrNull] so custom format codes work out of
         * the box.
         *
         * @param ref reference string, e.g. `"json-schema:UserSchema"`
         */
        @JvmStatic
        fun parse(ref: String): SchemaRef? {
            val colonIndex = ref.indexOf(':')
            if (colonIndex <= 0 || colonIndex == ref.length - 1) return null

            val formatCode = ref.substring(0, colonIndex)
            val name = ref.substring(colonIndex + 1)

            val format = SchemaFormat.fromCodeOrNull(formatCode) ?: return null
            return SchemaRef(format, name)
        }
    }
}
