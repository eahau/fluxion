package com.fluxion.core.function

import com.fluxion.core.enums.NodeType
import com.fluxion.core.exception.FunctionNotFoundException
import com.fluxion.core.util.uncheckedCast
import com.fluxion.core.value.FunctionMeta
import org.slf4j.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 函数注册中心 — 纯 Kotlin，零框架依赖
 *
 * 支持函数多版本共存：
 * - 代码内置函数（BUILTIN / CUSTOM）使用版本 0，直接替换 ACTIVE 版本
 * - 热发布函数（SCRIPT / EXTERNAL）使用递增版本号，旧版本进入 RETIRING 状态
 *   后继续服务在途调用，无在途调用后可被清理
 *
 * 解析优先级：CUSTOM > BUILTIN > SCRIPT > EXTERNAL
 */
class FunctionRegistry {

    private val log = LoggerFactory.getLogger(javaClass)

    /** functionName / functionNameWithPrefix -> VersionedFunction */
    private val functions = ConcurrentHashMap<String, VersionedFunction>()

    /** 当前 ACTIVE 版本的元信息 */
    private val metaRegistry = ConcurrentHashMap<String, FunctionMeta>()

    // ─── 注册 API ─────────────────────────────────────────────────

    /**
     * 代码注册函数（内置 / 自定义函数）。
     *
     * 使用版本号 0，直接替换 ACTIVE 版本，不产生退役版本。
     */
    fun register(name: String, meta: FunctionMeta, function: WorkflowFunction<*>) {
        val versioned = functions.computeIfAbsent(name) { VersionedFunction(name) }
        versioned.add(FunctionVersion(0, function.uncheckedCast<WorkflowFunction<Any>>()!!, meta))
        metaRegistry[name] = meta
        log.debug { "Registered workflow function: $name" }
    }

    fun register(name: String, function: WorkflowFunction<*>) {
        register(name, FunctionMeta.of(name), function)
    }

    fun registerAll(functions: Map<String, WorkflowFunction<*>>) {
        functions.forEach { (name, fn) -> register(name, fn) }
    }

    /**
     * 注册热发布函数。
     *
     * 使用 [version] 作为版本号，原 ACTIVE 版本进入 RETIRING。
     */
    fun register(name: String, version: Long, meta: FunctionMeta, function: WorkflowFunction<*>) {
        val versioned = functions.computeIfAbsent(name) { VersionedFunction(name) }
        versioned.add(FunctionVersion(version, function.uncheckedCast<WorkflowFunction<Any>>()!!, meta))
        metaRegistry[name] = meta
        log.info { "Registered workflow function: $name version=$version" }
    }

    // ─── 注销 API ─────────────────────────────────────────────────

    fun unregister(name: String) {
        functions.remove(name)?.remove()
        metaRegistry.remove(name)
        log.info { "Unregistered workflow function: $name" }
    }

    // ─── 解析 API ─────────────────────────────────────────────────

    fun resolve(functionRef: String, nodeType: NodeType?): WorkflowFunction<Any> {
        return findVersioned(functionRef, nodeType)?.resolve()
            ?: throw FunctionNotFoundException(functionRef)
    }

    fun resolve(functionRef: String): WorkflowFunction<Any> = resolve(functionRef, null)

    /**
     * 解析指定版本。
     *
     * 优先从 ACTIVE 查找，其次从 RETIRING 查找，用于重放、调试或显式版本绑定场景。
     */
    fun resolve(functionRef: String, nodeType: NodeType?, version: Long): WorkflowFunction<Any> {
        return findVersioned(functionRef, nodeType)?.resolve(version)
            ?: throw FunctionNotFoundException("$functionRef@$version")
    }

    private fun findVersioned(functionRef: String, nodeType: NodeType?): VersionedFunction? {
        functions[functionRef]?.let { return it }

        val prefixed = when (nodeType) {
            NodeType.BUILTIN -> "builtin:$functionRef"
            NodeType.SCRIPT -> "script:$functionRef"
            NodeType.EXTERNAL -> "external:$functionRef"
            else -> null
        }
        prefixed?.let { functions[it]?.let { v -> return v } }

        return null
    }

    // ─── 查询 API ─────────────────────────────────────────────────

    fun contains(functionRef: String): Boolean = functions.containsKey(functionRef)

    fun listAll(): List<FunctionMeta> = metaRegistry.values.toList()

    fun getMeta(functionRef: String): FunctionMeta? = metaRegistry[functionRef]

    fun size(): Int = functions.size

    fun names(): Collection<String> = functions.keys

    /** 获取指定函数当前 ACTIVE 版本号 */
    fun activeVersion(functionRef: String): Long? = functions[functionRef]?.activeVersion()

    /** 获取指定函数当前 RETIRING 版本号 */
    fun retiringVersion(functionRef: String): Long? = functions[functionRef]?.retiringVersion()

    /** 尝试清理指定函数的 RETIRING 版本（仅当无在途调用时） */
    fun purgeRetiring(functionRef: String): Boolean = functions[functionRef]?.purgeRetiring() ?: false

    /** 清理所有可退役的函数版本 */
    fun purgeAllRetiring(): Int {
        var count = 0
        functions.values.forEach { if (it.purgeRetiring()) count++ }
        return count
    }
}
