package com.fluxion.schema.api

import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaRef

/**
 * Schema 引用解析器，负责将 [SchemaRef] 解析为具体的 [Schema] 对象。
 */
interface SchemaResolver {

    /**
     * 解析引用。
     *
     * @param ref Schema 引用
     * @return 解析后的 Schema，未找到时返回 null
     */
    fun resolve(ref: SchemaRef): Schema?

    /**
     * 解析引用字符串。
     *
     * @param ref 引用字符串，如 `json-schema:UserSchema`
     * @return 解析后的 Schema，无法识别或未找到时返回 null
     */
    fun resolve(ref: String): Schema? {
        val schemaRef = SchemaRef.parse(ref) ?: return null
        return resolve(schemaRef)
    }
}
