package com.fluxion.core.exception

import com.fluxion.core.value.SideEffect

/**
 * 工作流异常基类
 *
 * errorCode 格式：WF-{分类}-{序号}，例如 WF-VALIDATION-001、WF-NODE-001
 *
 * ── 设计原则 ──────────────────────────────────────────────────────
 * 引擎只抛出携带 errorCode 的 WorkflowException；
 * 如何将 errorCode 转换为 HTTP/RPC 响应格式，由适配器层通过配置的
 * errorHandlerRef 函数完成 — 不在 core 中硬编码任何 HTTP 状态码映射。
 */
open class WorkflowException(
    /** 结构化错误码，供适配器路由到对应的错误处理函数 */
    val errorCode: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ─── Validation ─────────────────────────────────────────────────

/** WF-VALIDATION-001 入参校验失败 */
class InvalidParamException : WorkflowException {
    constructor(message: String) : super("WF-VALIDATION-001", message)
    constructor(errors: List<String>) : super("WF-VALIDATION-001", "Invalid parameters: $errors")
}

/** WF-VALIDATION-002 JSON Schema 校验失败 */
class SchemaValidationException(
    message: String,
    val validationErrors: List<String> = listOf(message)
) : WorkflowException("WF-VALIDATION-002", message)

/** WF-400-003 工作流定义无效 */
class InvalidWorkflowDefinitionException(message: String) :
    WorkflowException("WF-VALIDATION-003", message)

// ─── Node ───────────────────────────────────────────────────────

/** WF-NODE-001 节点执行异常 */
class WorkflowNodeException private constructor(
    val nodeName: String?,
    message: String,
    cause: Throwable?
) : WorkflowException("WF-NODE-001", message, cause) {

    /** 节点执行失败（含节点名，引擎 handleNodeError 路径） */
    constructor(nodeName: String, cause: Throwable?) : this(
        nodeName,
        "Node [$nodeName] execution failed: ${cause?.message ?: "unknown"}",
        cause
    )

    companion object {
        /** 超时 / 中断等纯消息场景 */
        @JvmStatic
        fun timeout(message: String, cause: Throwable?): WorkflowNodeException =
            WorkflowNodeException(null, message, cause)
    }
}

/** WF-NODE-002 重试耗尽 */
class RetryExhaustedException(nodeName: String, maxRetries: Int, lastCause: Throwable?) :
    WorkflowException(
        "WF-NODE-002",
        "Node [$nodeName] exhausted $maxRetries retries: ${lastCause?.message ?: "unknown"}",
        lastCause
    )

/** WF-NODE-003 降级函数执行失败 */
class FallbackFailedException(nodeName: String, cause: Throwable?) :
    WorkflowException(
        "WF-NODE-003",
        "Fallback of node [$nodeName] failed: ${cause?.message ?: "unknown"}",
        cause
    )

/** WF-NODE-004 节点执行超时 */
class NodeTimeoutException(nodeName: String, timeoutMs: Int) :
    WorkflowException("WF-NODE-004", "Node [$nodeName] timed out after ${timeoutMs}ms")

// ─── DAG ────────────────────────────────────────────────────────

/** WF-DAG-001 DAG 存在循环依赖 */
class CyclicDependencyException(workflowId: String) :
    WorkflowException("WF-DAG-001", "Cyclic dependency detected in workflow: $workflowId")

/** WF-DAG-002 依赖节点输出未就绪 */
class DependencyNotReadyException(nodeId: String, depNodeId: String) :
    WorkflowException("WF-DAG-002", "Node [$nodeId] dependency [$depNodeId] output not ready")

/** WF-DAG-003 重复节点 ID */
class DuplicateNodeIdException(nodeId: String) :
    WorkflowException("WF-DAG-003", "Duplicate node id: $nodeId")

// ─── Registry ───────────────────────────────────────────────────

/** WF-REGISTRY-001 函数未找到 */
class FunctionNotFoundException(functionRef: String) :
    WorkflowException("WF-REGISTRY-001", "Function not found: $functionRef")

/** WF-REGISTRY-002 工作流定义未找到 */
class WorkflowNotFoundException(workflowId: String) :
    WorkflowException("WF-REGISTRY-002", "Workflow not found: $workflowId")

/** WF-REGISTRY-003 装饰器未找到 */
class DecoratorNotFoundException(decoratorRef: String) :
    WorkflowException("WF-REGISTRY-003", "Decorator not found: $decoratorRef")

// ─── Saga / Transaction ─────────────────────────────────────────

/** WF-SAGA-001 Saga 补偿执行失败 */
class SagaExecutionException : WorkflowException {
    val pendingSideEffects: List<SideEffect>

    constructor(message: String, pendingSideEffects: List<SideEffect>) : super("WF-SAGA-001", message) {
        this.pendingSideEffects = pendingSideEffects
    }

    constructor(message: String, cause: Throwable?) : super("WF-SAGA-001", message, cause) {
        this.pendingSideEffects = emptyList()
    }
}

/** WF-TX-001 事务异常 */
class WorkflowTransactionException : WorkflowException {
    constructor(message: String, cause: Throwable?) : super("WF-TX-001", message, cause)
    constructor(message: String) : super("WF-TX-001", message)
}

// ─── External ───────────────────────────────────────────────────

/** WF-EXTERNAL-001 外部函数网关调用失败 */
class ExternalFunctionException(functionRef: String, cause: Throwable?) :
    WorkflowException(
        "WF-EXTERNAL-001",
        "External function [$functionRef] call failed: ${cause?.message ?: "unknown"}",
        cause
    )

// ─── Decorator ──────────────────────────────────────────────────

/** WF-DECORATOR-001 限流：请求过多 */
class RateLimitExceededException(message: String) : WorkflowException("WF-DECORATOR-001", message)

/** WF-DECORATOR-002 分布式锁获取失败 */
class LockAcquisitionException(message: String) : WorkflowException("WF-DECORATOR-002", message)

// ─── Bootstrap ──────────────────────────────────────────────────

/** WF-500-012 元工作流自举失败 */
class MetaWorkflowBootstrapException : WorkflowException {
    constructor(message: String) : super("WF-500-012", message)
    constructor(message: String, cause: Throwable?) : super("WF-500-012", message, cause)
}
