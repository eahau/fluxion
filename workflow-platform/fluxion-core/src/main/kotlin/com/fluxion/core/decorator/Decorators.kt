package com.fluxion.core.decorator

import com.fluxion.core.exception.DecoratorNotFoundException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.WorkflowNode
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 节点装饰器 SPI
 * 通过包装 WorkflowFunction 实现横切关注点（Metrics / Trace / RateLimit / Cache / Async）
 */
interface NodeDecorator {
    /** 装饰器引用名（全局唯一，用于 WorkflowNode.decorators 配置） */
    fun name(): String

    /**
     * 包装 function，返回新的 WorkflowFunction
     *
     * @param function 被包装的原始函数（或上一层装饰器包装后的函数）
     * @param node     当前节点（装饰器可读取节点的 decoratorParams 获取自己的配置）
     */
    fun decorate(function: WorkflowFunction<Any>, node: WorkflowNode): WorkflowFunction<Any>
}

/**
 * 装饰器注册中心 — 纯 Kotlin，零框架依赖
 */
class DecoratorRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val decorators = ConcurrentHashMap<String, NodeDecorator>()

    fun register(decorator: NodeDecorator) {
        decorators[decorator.name()] = decorator
        log.debug { "Registered decorator: ${decorator.name()}" }
    }

    fun registerAll(decoratorList: Collection<NodeDecorator>) {
        decoratorList.forEach(::register)
    }

    fun resolve(name: String): NodeDecorator =
        decorators[name] ?: throw DecoratorNotFoundException(name)

    fun contains(name: String): Boolean = decorators.containsKey(name)

    fun listNames(): List<String> = decorators.keys().toList()
}

/**
 * 异步回调状态
 */
enum class AsyncCallbackStatus {
    SUCCESS,
    FAILED
}

/**
 * 异步回调上下文 — 承载异步节点执行完成后的完整上下文与结果。
 *
 * 由 [AsyncDecorator] 在异步任务执行完成后构造，并通过 [AsyncCallback.publish] 投递给后端实现。
 */
data class AsyncCallbackContext(
    /** 本次异步任务唯一标识（调用方立即可得） */
    val asyncId: String,
    /** 工作流执行唯一标识 */
    val executionId: String,
    /** 工作流定义 ID */
    val workflowId: String,
    /** 工作流名称 */
    val workflowName: String,
    /** 所属应用分组 */
    val appGroup: String?,
    /** 节点唯一 ID */
    val nodeId: String,
    /** 节点名称 */
    val nodeName: String,
    /** 节点引用的函数 */
    val functionRef: String,
    /** 执行结果状态：SUCCESS / FAILED */
    val status: AsyncCallbackStatus,
    /** 成功时的节点输出 */
    val output: Any?,
    /** 失败时的错误信息 */
    val errorMsg: String?
)

/**
 * 异步回调 SPI — AsyncDecorator 的后端依赖
 * 实现由 workflow-admin 提供（Kafka / HTTP 回调）
 */
interface AsyncCallback {
    fun publish(context: AsyncCallbackContext)
}

/**
 * 缓存存储 SPI — CacheDecorator 的后端依赖
 * 默认实现：workflow-builtin-functions-core 提供 CaffeineCacheStore
 * 可替换实现：由上层应用注入 RedisCacheStore 等 Bean 覆盖
 */
interface CacheStore {
    fun get(key: String): Any?
    fun put(key: String, value: Any?, ttl: Long, unit: TimeUnit)
    fun evict(key: String)
}
