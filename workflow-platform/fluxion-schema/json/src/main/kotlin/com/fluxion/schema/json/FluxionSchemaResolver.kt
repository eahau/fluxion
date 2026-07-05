package com.fluxion.schema.json

import com.fluxion.schema.api.SchemaRegistry
import com.fluxion.schema.api.SchemaResolver
import com.fluxion.schema.model.Schema
import com.fluxion.schema.model.SchemaRef

/**
 * Fluxion 引用解析器，支持 `schema:<name>`、`protobuf:<name>`、`avro:<name>`。
 *
 * 当前通过 [SchemaRegistry] 按名称查找，未来可扩展版本号支持。
 */
class FluxionSchemaResolver(
    private val registry: SchemaRegistry
) : SchemaResolver {

    override fun resolve(ref: SchemaRef): Schema? {
        return registry.get(ref.name, ref.version)
    }
}
