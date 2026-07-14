package com.fluxion.schema.model

/**
 * Opaque schema-format identifier — wraps a raw string code so the core
 * module can route to the correct SPI implementations without hard-coding
 * a closed enum of formats.
 *
 * The wrapper is a Kotlin `value class` so there is zero allocation at
 * runtime; the string code carries all semantics. Callers construct formats
 * via [fromCode] / [fromCodeOrNull] which normalize case and return the
 * shared singleton constants for the three built-in formats (JSON Schema,
 * Protobuf, Avro) — reference-equality comparisons therefore work for the
 * common cases.
 *
 * Third-party formats (FlatBuffers, Thrift, XML Schema, …) are supported by
 * design: passing any non-blank code to [fromCode] returns a new instance
 * that extension modules can then bind a [SchemaFormatBundle] against.
 */
@JvmInline
value class SchemaFormat(val code: String) {

    init {
        require(code.isNotBlank()) { "SchemaFormat code must not be blank" }
    }

    companion object {
        /** JSON Schema (draft-07 and compatible versions). */
        val JSON_SCHEMA = SchemaFormat("json-schema")

        /** Protobuf — represented as a `FileDescriptorProto` JSON serialization. */
        val PROTOBUF = SchemaFormat("protobuf")

        /** Apache Avro — standard `.avsc` JSON representation. */
        val AVRO = SchemaFormat("avro")

        /**
         * Resolves a format code, returning the shared singleton for known
         * formats or a new instance for unknown codes.
         *
         * @throws IllegalArgumentException if [code] is blank after trimming.
         */
        @JvmStatic
        fun fromCode(code: String): SchemaFormat = fromCodeOrNull(code)
            ?: throw IllegalArgumentException("SchemaFormat code must not be blank")

        /**
         * Lenient resolver — returns `null` for blank / null inputs instead
         * of throwing. Used when parsing HTTP headers, user input, or config
         * files where "absent" is a valid state.
         */
        @JvmStatic
        fun fromCodeOrNull(code: String?): SchemaFormat? {
            val normalized = code?.trim()?.lowercase() ?: return null
            if (normalized.isEmpty()) return null
            return when (normalized) {
                JSON_SCHEMA.code -> JSON_SCHEMA
                PROTOBUF.code -> PROTOBUF
                AVRO.code -> AVRO
                else -> SchemaFormat(normalized)
            }
        }
    }

    override fun toString(): String = code
}
