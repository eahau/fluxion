package com.fluxion.admin.mapper

import com.fluxion.core.mock.MockConfig
import com.fluxion.core.util.JsonUtil

/**
 * OpenAPI 生成的 MockConfig DTO → workflow-core MockConfig 转换器。
 *
 * 由于生成的 Java Bean 与 core 层的 Kotlin data class 位于不同模块/包，
 * 利用 JSON 结构一致的特点做无样板转换。
 */
object MockConfigMapper {

    fun toCoreMockConfig(dto: com.fluxion.admin.generated.model.MockConfig?): MockConfig {
        if (dto == null) return MockConfig.EMPTY
        return JsonUtil.convertValue(dto, MockConfig::class.java)
    }
}
