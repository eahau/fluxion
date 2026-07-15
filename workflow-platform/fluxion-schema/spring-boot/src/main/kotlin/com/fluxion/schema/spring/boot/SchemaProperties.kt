package com.fluxion.schema.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fluxion.schema")
data class SchemaProperties(
    var enabled: Boolean = true,
    var jsonSchemaVersion: String = "V7",
    var cache: CacheProperties = CacheProperties()
) {
    data class CacheProperties(
        var parseTtlMinutes: Long = 30,
        var parseMaxSize: Long = 1000,
        var schemaTtlMinutes: Long = 5,
        var schemaMaxSize: Long = 500,
        var preloadOnStartup: Boolean = true,
        var pollIntervalMs: Long = 30000
    )
}
