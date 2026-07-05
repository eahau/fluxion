package com.fluxion.core.lock

import com.fluxion.core.decorator.WorkflowDecorator
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.model.decoratorParams
import com.fluxion.core.value.EngineResult
import org.slf4j.*

/**
 * 工作流级分布式锁装饰器。
 *
 * 在整段工作流执行前后加锁，确保同一锁键的工作流请求串行执行。
 * 适用于需要跨节点、跨调用保持一致性的场景（如库存扣减、订单状态机）。
 *
 * 配置参数（通过 [WorkflowDefinition.workflowDecoratorParams] 的 "workflow:lock" 键）：
 * - `lockKey`: 静态锁键，优先级最高
 * - `lockKeyExpression`: 锁键模板，支持 `${'$'}{path}` 占位符（path 为点号嵌套路径），可用变量：
 *     input      — 工作流原始入参
 *     workflowId — 工作流 ID
 *   例如：`"lock:${'$'}{workflowId}:${'$'}{input.userId}"`
 * - `waitMillis`:  最大等待锁时间（毫秒），默认 0（拿不到立即失败）
 * - `leaseMillis`: 锁最大持有时间（毫秒），默认 30000
 * - `retry`:       获取失败后的重试次数，默认 0
 * - `retryIntervalMillis`: 重试间隔（毫秒），默认 100
 * - `sync`:        是否同步阻塞获取锁，默认 false（为 true 时拿不到锁一直阻塞，慎用）
 * - `failOnLocked`: 获取锁失败时是否抛异常，默认 true
 * - `prefix`:      锁键前缀，默认 "fluxion:lock:workflow:"
 *
 * 锁键生成优先级：lockKey > lockKeyExpression > 默认 "{prefix}{workflowId}"
 */
class WorkflowLockDecorator(
    private val lockProvider: DistributedLockProvider
) : WorkflowDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun name(): String = "workflow:lock"

    override suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult {
        val context = LockContext(
            params = def.decoratorParams(name()),
            appGroup = def.appGroup,
            domain = def.id,
            defaultPrefix = "fluxion:lock:workflow:",
            defaultBusinessKey = def.id,
            contextDescription = "workflow [${def.id}]",
            variables = mapOf(
                "input" to rawInput,
                "workflowId" to def.id
            )
        )
        return lockProvider.lockSuspending(context, log, execute)
    }
}
