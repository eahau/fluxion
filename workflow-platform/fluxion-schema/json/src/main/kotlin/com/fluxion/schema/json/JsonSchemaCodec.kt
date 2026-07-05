package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.model.Schema
import com.fluxion.schema.util.JsonUtil

/**
 * JSON Schema 编解码器。
 *
 * 使用标准 Jackson JSON 序列化/反序列化，适用于 JSON Schema 格式的数据。
 * 这是最通用的 codec，也是工作流运行时的默认编解码器。
 */
class JsonSchemaCodec : SchemaCodec {

    override fun serialize(data: Any?, schema: Schema): ByteArray {
        return JsonUtil.serialize(data).toByteArray(Charsets.UTF_8)
    }

    override fun deserialize(bytes: ByteArray, schema: Schema): Any? {
        val json = String(bytes, Charsets.UTF_8)
        return JsonUtil.deserialize(json, Any::class.java)
    }

    override fun toJson(data: Any?, schema: Schema): String {
        return JsonUtil.serialize(data)
    }

    override fun fromJson(json: String, schema: Schema): Any? {
        return JsonUtil.deserialize(json, Any::class.java)
    }
}
