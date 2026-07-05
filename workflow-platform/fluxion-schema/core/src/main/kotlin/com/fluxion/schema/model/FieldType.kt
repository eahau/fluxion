package com.fluxion.schema.model

/**
 * 跨格式统一的字段类型抽象。
 *
 * 覆盖 JSON Schema、Protobuf、Avro 的常见原子类型与复合类型。
 */
enum class FieldType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    OBJECT,
    ARRAY,
    NULL,
    ANY,
    MESSAGE,    // Protobuf Message
    RECORD      // Avro Record
}
