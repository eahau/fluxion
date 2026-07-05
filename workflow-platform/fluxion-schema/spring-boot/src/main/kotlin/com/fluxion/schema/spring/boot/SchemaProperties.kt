package com.fluxion.schema.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * fluxion-schema 配置属性。
 */
@ConfigurationProperties(prefix = "fluxion.schema")
data class SchemaProperties(
    /**
     * 是否启用 fluxion-schema 自动装配。
     */
    var enabled: Boolean = true,

    /**
     * JSON Schema 默认版本，当前仅支持 draft-07。
     */
    var jsonSchemaVersion: String = "V7"
)
