package com.fluxion.core.enums

/**
 * 节点错误处理策略
 * 配置在 WorkflowNode.errorStrategy，决定节点异常时引擎的行为
 */
enum class ErrorStrategy {
    /** 抛出异常，中止整个工作流 */
    FAIL,
    /** 跳过本节点，继续执行下一节点（output 为 null） */
    SKIP,
    /** 调用 WorkflowFunction.fallback() 获取降级输出，继续执行 */
    FALLBACK,
    /** 按 retryCount / retryBaseMs 指数退避重试 */
    RETRY
}
