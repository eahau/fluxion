/**
 * Spring Boot `@ConfigurationProperties` binding for the `fluxion.schema`
 * configuration namespace.
 *
 * Exposed by [FluxionSchemaAutoConfiguration] so consumers can toggle the
 * auto-configuration or tweak schema-version defaults via
 * `application.properties` / `application.yml`.
 */
package com.fluxion.schema.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Typed configuration properties for the fluxion-schema Spring Boot starter.
 *
 * @property enabled           master switch for the auto-configuration; set to
 *                             `false` when wiring schemas manually in tests.
 * @property jsonSchemaVersion JSON Schema dialect version string. Currently
 *                             only `"V7"` (draft-07) is supported by the
 *                             embedded networknt validator.
 */
@ConfigurationProperties(prefix = "fluxion.schema")
data class SchemaProperties(
    var enabled: Boolean = true,
    var jsonSchemaVersion: String = "V7"
)
