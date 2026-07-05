package com.fluxion.core.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.model.WorkflowDefinition
import com.fluxion.core.value.EngineResult
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 工作流级装饰器 SPI。
 *
 * 与 [NodeDecorator] 的区别：
 * - 作用域：包裹整段工作流执行（[DagExecutor.execute] 级别），而非单个节点。
 * - 输入上下文：原始工作流入参 + [WorkflowDefinition]，而非 [com.fluxion.core.model.NodeInput]。
 * - 异常语义：工作流级装饰器抛出的异常通常直接导致整个工作流失败，不受节点 errorStrategy 影响。
 *
 * 典型用途：
 * - 工作流级分布式锁（[com.fluxion.core.lock.WorkflowLockDecorator]）
 * - 工作流级事务（[com.fluxion.builtin.db.transaction.WorkflowTransactionDecorator]）
 * - 工作流级限流（[com.fluxion.core.ratelimit.WorkflowRateLimitDecorator]）
 */
interface WorkflowDecorator {
    /** 装饰器引用名（全局唯一，用于 WorkflowDefinition.workflowDecorators 配置） */
    fun name(): String

    /**
     * 装饰整段工作流执行。
     *
     * 实现类应在 [execute] 前后插入横切逻辑，最终必须调用 [execute] 完成实际工作流执行。
     *
     * @param def      工作流定义
     * @param rawInput 工作流原始入参
     * @param execute  被装饰的实际执行逻辑（调用一次）
     * @return 工作流执行结果
     */
    suspend fun decorate(
        def: WorkflowDefinition,
        rawInput: Map<String, Any>,
        execute: suspend () -> EngineResult
    ): EngineResult
}

/**
 * 工作流级装饰器注册中心 — 纯 Kotlin，零框架依赖。
 */
class WorkflowDecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, WorkflowDecorator>()

    fun register(decorator: WorkflowDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered workflow decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<WorkflowDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): WorkflowDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}
