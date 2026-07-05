package com.fluxion.schema.api

/**
 * Schema 编解码器（运行时序列化 / 反序列化抽象）。
 *
 * 在工作流运行时，根据 Schema 的实际格式类型选择对应的序列化/反序列化机制：
 * - JSON Schema：标准 JSON 序列化（Jackson）
 * - Avro：Avro JSON / Binary 编码
 * - Protobuf：Protobuf JSON / Binary 编码
 *
 * 格式路由由 [CodecBinding] 在 Spring 装配层声明，SPI 本身不感知格式。
 */
interface SchemaCodec {

    /**
     * 将数据对象序列化为字节数组（二进制传输格式）。
     *
     * @param data   待序列化的数据对象（通常为 Map / POJO）
     * @param schema 已编译的 Schema（用于约束序列化格式）
     * @return 序列化后的字节数组
     */
    fun serialize(data: Any?, schema: com.fluxion.schema.model.Schema): ByteArray

    /**
     * 将字节数组反序列化为数据对象。
     *
     * @param bytes  序列化数据
     * @param schema 已编译的 Schema（用于约束反序列化目标类型）
     * @return 反序列化后的数据对象
     */
    fun deserialize(bytes: ByteArray, schema: com.fluxion.schema.model.Schema): Any?

    /**
     * 将数据对象序列化为 JSON 字符串（文本传输格式）。
     *
     * @param data   待序列化的数据对象
     * @param schema 已编译的 Schema
     * @return JSON 字符串
     */
    fun toJson(data: Any?, schema: com.fluxion.schema.model.Schema): String

    /**
     * 将 JSON 字符串反序列化为数据对象。
     *
     * @param json   JSON 字符串
     * @param schema 已编译的 Schema
     * @return 反序列化后的数据对象
     */
    fun fromJson(json: String, schema: com.fluxion.schema.model.Schema): Any?
}
