package com.fluxion.core.mock

/**
 * 工作流调试 Mock 配置 — 零 Spring 依赖，可被 [DebugSnapshot] 序列化携带。
 *
 * @param enabled 总开关
 * @param rules   按函数引用分组的 Mock 规则列表
 */
data class MockConfig(
    val enabled: Boolean = true,
    val rules: List<MockRule> = emptyList()
) {
    companion object {
        /** 空配置，等价于未启用 mock */
        val EMPTY = MockConfig()
    }
}

/**
 * 单条 Mock 规则。
 *
 * 命中逻辑：
 * 1. `enabled == true`
 * 2. 当前节点 [functionRef] 匹配
 * 3. `condition` 为空 或条件命中
 *
 * 响应选择优先级：`response` 静态值 > `responseTemplate` 模板渲染。
 *
 * @param id               规则唯一标识（前端生成，便于编辑/删除）
 * @param functionRef      目标函数引用，如 `custom:userService.queryById`
 * @param enabled          规则开关
 * @param condition        命中条件（可选）
 * @param response         静态响应值（任意 JSON 可序列化对象）
 * @param responseTemplate 字符串模板，支持 `${var}` / `${var:default}` 与 `#{jexlExpr}`
 * @param delayMs          模拟延迟（毫秒）
 */
data class MockRule(
    val id: String = "",
    val functionRef: String = "",
    val enabled: Boolean = true,
    val condition: MockCondition? = null,
    val response: Any? = null,
    val responseTemplate: String? = null,
    val delayMs: Int = 0
)

/**
 * Mock 命中条件 — 支持 JEXL 表达式和字段匹配器两种形式。
 *
 * 两者同时存在时为 AND 关系；同时为空视为恒命中。
 */
data class MockCondition(
    /** JEXL 表达式，可用变量见 [MockEngine.renderTemplate] */
    val expression: String? = null,
    /** 字段匹配器，key 为展平后的字段路径 */
    val fieldMatchers: Map<String, FieldMatcher>? = null
)

/**
 * 字段值匹配器 — 所有规则可选，任一命中即通过。
 *
 * 匹配优先级：exact → startsWith → endsWith → contains，短路返回。
 */
data class FieldMatcher(
    val exact: Set<String> = emptySet(),
    val startsWith: List<String> = emptyList(),
    val endsWith: List<String> = emptyList(),
    val contains: List<String> = emptyList()
) {
    /**
     * 判断给定值是否命中任一规则。
     * null 或空串视为不命中。
     */
    fun match(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        return exact.contains(value)
            || startsWith.any { value.startsWith(it) }
            || endsWith.any { value.endsWith(it) }
            || contains.any { value.contains(it) }
    }

    /** 是否为空匹配器 */
    fun isEmpty(): Boolean = exact.isEmpty() && startsWith.isEmpty() && endsWith.isEmpty() && contains.isEmpty()
}
