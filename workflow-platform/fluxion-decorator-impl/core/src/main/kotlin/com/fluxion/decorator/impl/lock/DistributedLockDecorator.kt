package com.fluxion.decorator.impl.lock

import com.fluxion.core.decorator.NodeDecorator
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.lock.*
import com.fluxion.core.model.WorkflowNode
import com.fluxion.core.model.decoratorParams
import org.slf4j.*

/**
 * 分布式锁装饰器 — 函数级互斥。
 *
 * 在节点执行前尝试获取分布式锁，执行后（无论成功或失败）释放。
 * 适用于对同一函数或同一业务键的并发调用进行串行化。
 *
 * 配置参数（通过 [WorkflowNode.decoratorParams] 的 "lock:distributed" 键）：
 * - `lockKey`: 静态锁键，优先级最高
 * - `lockKeyExpression`: 锁键模板，支持 `${'$'}{path}` 占位符（path 为点号嵌套路径），可用变量：
 *     input        — 当前节点的 directInput
 *     nodeId       — 节点 ID
 *     nodeName     — 节点名称
 *     workflowId   — 工作流 ID
 *     executionId  — 执行 ID
 *     functionRef  — 函数引用
 *   例如：`"lock:${'$'}{workflowId}:${'$'}{nodeId}"`
 * - `waitMillis`:  最大等待锁时间（毫秒），默认 0（拿不到立即失败）
 * - `leaseMillis`: 锁最大持有时间（毫秒），默认 30000
 * - `retry`:       获取失败后的重试次数，默认 0
 * - `retryIntervalMillis`: 重试间隔（毫秒），默认 100
 * - `sync`:        是否同步阻塞获取锁，默认 false（为 true 时拿不到锁一直阻塞，慎用）
 * - `failOnLocked`: 获取锁失败时是否抛异常，默认 true
 * - `prefix`:      锁键前缀，默认 "fluxion:lock:"
 *
 * 锁键生成优先级：lockKey > lockKeyExpression > 默认 "{prefix}{nodeId}"
 */
class DistributedLockDecorator(
    private val lockProvider: DistributedLockProvider
) : NodeDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "lock:distributed"

    override fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any> {
        return WorkflowFunction { input ->
            val context = LockContext(
                params = node.decoratorParams(name()),
                appGroup = node.appGroup,
                domain = node.workflowId,
                defaultPrefix = "fluxion:lock:",
                defaultBusinessKey = node.id,
                contextDescription = "node [${node.name}]",
                variables = mapOf(
                    "input" to input.directInput,
                    "nodeId" to node.id,
                    "nodeName" to node.name,
                    "workflowId" to node.workflowId,
                    "executionId" to (input.meta?.executionId ?: ""),
                    "functionRef" to node.functionRef
                )
            )
            lockProvider.lock(context, log) { function.apply(input) }
        }
    }
}
