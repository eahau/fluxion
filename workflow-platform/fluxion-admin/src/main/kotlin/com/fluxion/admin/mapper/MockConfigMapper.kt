package com.fluxion.admin.mapper

import com.fluxion.core.mock.MockConfig
import com.fluxion.core.util.JsonUtil

/**
 * Converts between the admin OpenAPI-generated `MockConfig` DTO and the workflow-core
 * `MockConfig` data class.
 *
 * Because the two types live in separate modules/packages but share an identical JSON
 * shape, the conversion is performed via schema-free Jackson round-tripping rather than
 * field-by-field manual assignment.
 */
object MockConfigMapper {

    /**
     * Convert an admin mock DTO into its core-engine counterpart.
     * Returns `MockConfig.EMPTY` if the input is null so callers avoid null checks.
     */
    fun toCoreMockConfig(dto: com.fluxion.admin.generated.model.MockConfig?): MockConfig {
        if (dto == null) return MockConfig.EMPTY
        return JsonUtil.convertValue(dto, MockConfig::class.java)
    }
}
