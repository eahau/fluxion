package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

/**
 * [SchemaDataProvider] 注册表 — 根据 [SchemaFormat] 路由到对应的数据访问提供者。
 *
 * 接受 `Map<SchemaFormat, SchemaDataProvider>` 格式的路由表，查询为 O(1)。线程安全、无状态。
 *
 * ```kotlin
 * val provider = registry.getProvider(SchemaFormat.PROTOBUF)
 * val value = provider.getField(dynamicMessage, "name")
 * ```
 */
class SchemaDataProviderRegistry(
    private val providerMap: Map<SchemaFormat, SchemaDataProvider> = emptyMap()
) {

    companion object {
        /** 空注册表（无 provider），用于测试或不需要 Schema 数据访问的场景。 */
        @JvmField
        val EMPTY = SchemaDataProviderRegistry()
    }

    /**
     * 获取支持指定格式的 provider。
     *
     * @throws IllegalArgumentException 若没有匹配的 provider
     */
    fun getProvider(format: SchemaFormat): SchemaDataProvider {
        return providerMap[format]
            ?: throw IllegalArgumentException(
                "No SchemaDataProvider registered for format: ${format.code}. " +
                    "Available formats: ${providerMap.keys.map { it.code }}"
            )
    }

    /**
     * 尝试获取 provider，不存在时返回 null。
     */
    fun findProvider(format: SchemaFormat): SchemaDataProvider? = providerMap[format]

    /**
     * 是否注册了支持指定格式的 provider。
     */
    fun hasProvider(format: SchemaFormat): Boolean = format in providerMap

    /**
     * 获取所有已注册的 provider 列表。
     */
    fun allProviders(): List<SchemaDataProvider> = providerMap.values.toList()
}
