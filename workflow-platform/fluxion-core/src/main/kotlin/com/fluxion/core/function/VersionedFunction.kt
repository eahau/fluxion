package com.fluxion.core.function

import com.fluxion.core.exception.FunctionNotFoundException
import org.slf4j.*
import java.util.concurrent.atomic.AtomicReference

/**
 * 同名函数的多版本容器（最简模型）。
 *
 * 每个函数最多同时保留两个版本：
 * - ACTIVE：当前新执行使用的版本
 * - RETIRING：上一个 ACTIVE 版本，只服务已在运行的调用
 *
 * 当新版本发布时，旧 ACTIVE 进入 RETIRING；RETIRING 槽若已有版本，
 * 则直接丢弃其引用（任何在途调用仍持有该 FunctionVersion 对象，可安全完成）。
 */
class VersionedFunction(val functionName: String) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val active = AtomicReference<FunctionVersion?>()
    private val retiring = AtomicReference<FunctionVersion?>()

    /**
     * 注册新版本。
     *
     * - version == 0：视为代码内置函数，直接替换 ACTIVE，不产生退役版本
     * - version > 0：热发布函数，原 ACTIVE 进入 RETIRING
     */
    fun add(version: FunctionVersion) {
        if (version.version == 0L) {
            active.set(version)
            log.debug { "Registered code function [$functionName] with version=0" }
            return
        }

        val previousActive = active.getAndSet(version)
        previousActive?.retire()

        val previousRetiring = retiring.getAndSet(previousActive)
        previousRetiring?.let { dropRetiring(it) }

        log.info {
            "Registered function [$functionName] version=${version.version}, " +
                "previous=${previousActive?.version}, retiring dropped=${previousRetiring?.version}"
        }
    }

    /**
     * 解析当前 ACTIVE 版本。
     */
    fun resolve(): WorkflowFunction<Any> {
        val version = active.get() ?: throw FunctionNotFoundException(functionName)
        return version.toTrackingFunction()
    }

    /**
     * 解析指定版本（可从 ACTIVE 或 RETIRING 中查找）。
     */
    fun resolve(version: Long): WorkflowFunction<Any> {
        val target = active.get()?.takeIf { it.version == version }
            ?: retiring.get()?.takeIf { it.version == version }
            ?: throw FunctionNotFoundException("$functionName@$version")
        return target.toTrackingFunction()
    }

    /**
     * 下线该函数：清空 ACTIVE 和 RETIRING。
     */
    fun remove() {
        active.set(null)
        val oldRetiring = retiring.getAndSet(null)
        oldRetiring?.let { dropRetiring(it) }
        log.info { "Removed function [$functionName]" }
    }

    /** 当前 ACTIVE 版本号 */
    fun activeVersion(): Long? = active.get()?.version

    /** 当前 RETIRING 版本号 */
    fun retiringVersion(): Long? = retiring.get()?.version

    /** 尝试清理 RETIRING 版本（仅当无在途调用时） */
    fun purgeRetiring(): Boolean {
        val version = retiring.get() ?: return false
        if (version.inFlight == 0L) {
            val removed = retiring.compareAndSet(version, null)
            if (removed) {
                log.debug { "Purged retiring function [$functionName] version=${version.version}" }
            }
            return removed
        }
        return false
    }

    private fun dropRetiring(version: FunctionVersion) {
        log.debug {
            "Dropping retiring function [$functionName] version=${version.version}, " +
                "inFlight=${version.inFlight}"
        }
    }
}
