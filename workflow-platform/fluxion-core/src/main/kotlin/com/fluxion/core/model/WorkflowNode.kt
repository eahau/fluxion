package com.fluxion.core.model

import com.fluxion.core.enums.ErrorStrategy
import com.fluxion.core.enums.NodeType
import com.fluxion.core.util.uncheckedCastGeneric

/**
 * 工作流节点 — 类比调用栈的栈帧。
 *
 * 每个节点代表一次函数调用，包含函数引用、参数、错误策略、超时、重试、装饰器等配置。
 */
data class WorkflowNode(
    /** 节点唯一 ID（同一工作流内不重复） */
    var id: String = "",
    /** 节点名称（日志/调试显示用） */
    var name: String = "",
    /** 所属工作流 ID（运行时填充，配置时为 null） */
    var workflowId: String = "",
    /** 所属应用分组（运行时从 WorkflowDefinition 填充，配置时为 null） */
    var appGroup: String? = null,
    /** 函数引用（如 "builtin:httpCall"、"wf:errorWrapper"） */
    var functionRef: String = "",
    /** 节点类型（决定注册中心的解析策略） */
    var type: NodeType = NodeType.CUSTOM,
    /** 节点配置参数（运行时数据来自 NodeInput） */
    var params: Map<String, Any>? = null,
    /** 错误处理策略（FAIL/SKIP/FALLBACK/RETRY） */
    var errorStrategy: ErrorStrategy = ErrorStrategy.FAIL,
    /** 执行超时（毫秒，0=无超时） */
    var timeoutMs: Int = 0,
    /** 重试次数（0=不重试） */
    var retryCount: Int = 0,
    /** 重试基础延迟（毫秒，指数退避） */
    var retryBaseMs: Int = 0,
    /** 补偿函数引用（Saga 模式用） */
    var compensateFunctionRef: String? = null,
    /** 线性流下一节点 ID（DAG 模式下为 null） */
    var next: String? = null,
    /** 条件分支列表（按顺序匹配第一个为 true 的条件） */
    var conditionalNexts: List<ConditionalNext>? = null,
    /** 依赖节点 ID 列表（DAG 模式：前置节点完成后再执行） */
    var dependsOn: List<String>? = null,
    /** 装饰器列表（按顺序包裹，如 "logging:default"、"ratelimit:slidingWindow"） */
    var decorators: List<String>? = null,
    /** 装饰器参数（key=装饰器名，value=该装饰器的参数 Map） */
    var decoratorParams: Map<String, Map<String, Any>>? = null,
    /** 节点备注（画布显示用） */
    var remark: String? = null,
    /** 是否异步执行（true=不阻塞后续节点，异常不中止流程） */
    var asyncExecution: Boolean = false
) {
    companion object {
        /** 重试次数上限（与 Authing 一致，超过此值视为配置异常） */
        const val MAX_RETRY_COUNT = 5
    }

    /** 获取装饰器配置参数，带默认值 */
    fun <T> getDecoratorParam(decoratorName: String, paramKey: String, defaultValue: T): T {
        val p = decoratorParams?.get(decoratorName) ?: return defaultValue
        val v = p[paramKey] ?: return defaultValue
        return v.uncheckedCastGeneric()!!
    }

    /**
     * 校验节点配置合法性。
     * 当前仅校验 retryCount 不超过上限 [MAX_RETRY_COUNT]。
     */
    fun validate() {
        if (retryCount > MAX_RETRY_COUNT) {
            throw IllegalArgumentException(
                "Node [$id] retryCount=$retryCount exceeds maximum allowed ($MAX_RETRY_COUNT)"
            )
        }
    }
}
