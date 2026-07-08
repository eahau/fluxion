package com.fluxion.schema.api

import com.fluxion.schema.model.SchemaFormat

/**
 * Immutable registry of [SchemaDataProvider] instances keyed by [SchemaFormat].
 *
 * Thin wrapper over `Map<SchemaFormat, SchemaDataProvider>` — O(1) lookup,
 * thread-safe (stateless once constructed). Spring Boot auto-config builds
 * this registry by collecting every [SchemaFormatBundle]'s `dataProvider`
 * and assembling them into a single map.
 *
 * Usage:
 * ```kotlin
 * val provider = registry.getProvider(SchemaFormat.PROTOBUF)
 * val value = provider.getField(dynamicMessage, "name")
 * ```
 */
class SchemaDataProviderRegistry(
    private val providerMap: Map<SchemaFormat, SchemaDataProvider> = emptyMap()
) {

    companion object {
        /** Empty registry — for tests or deployments that don't use schema data access. */
        @JvmField
        val EMPTY = SchemaDataProviderRegistry()
    }

    /**
     * Returns the provider for [format].
     *
     * @throws IllegalArgumentException if no provider is registered for the format.
     */
    fun getProvider(format: SchemaFormat): SchemaDataProvider {
        return providerMap[format]
            ?: throw IllegalArgumentException(
                "No SchemaDataProvider registered for format: ${format.code}. " +
                    "Available formats: ${providerMap.keys.map { it.code }}"
            )
    }

    /** Safe lookup — returns `null` instead of throwing. */
    fun findProvider(format: SchemaFormat): SchemaDataProvider? = providerMap[format]

    /** True iff a provider is registered for the given format. */
    fun hasProvider(format: SchemaFormat): Boolean = format in providerMap

    /** Returns all registered providers (for iteration / diagnostics). */
    fun allProviders(): List<SchemaDataProvider> = providerMap.values.toList()
}
