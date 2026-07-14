/**
 * JSON-format [SchemaCodec] backed by the shared Jackson [JsonUtil] helper.
 *
 * Serialisation always produces UTF-8 encoded compact JSON; deserialisation
 * uses Jackson's polymorphic `Any` deserializer so Map entries become
 * LinkedHashMap and arrays become ArrayList. The same logic is reused for
 * both `toJson`/`fromJson` string round-trips and `serialize`/`deserialize`
 * byte-array calls.
 *
 * This is the most commonly wired codec in the platform because JSON Schema
 * format is the default for inline node-level validation rules and for
 * function param/output schemas authored through the admin UI.
 */
package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaCodec
import com.fluxion.schema.model.Schema
import com.fluxion.schema.util.JsonUtil

/**
 * Schema codec implementation for JSON Schema format data.
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
