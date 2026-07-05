package com.fluxion.core.function.external

import org.slf4j.*
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 外部函数 Transport 注册中心。
 *
 * 支持两种发现方式：
 * 1. 显式传入（构造参数或 [register]）——用于测试、程序化配置或 Spring Bean 注入。
 * 2. Java [ServiceLoader]（`META-INF/services/com.fluxion.core.function.external.ExternalFunctionTransport`）
 *    ——零框架依赖，非 Spring 项目可直接通过 classpath 暴露 transport 实现。
 *
 * 同协议重复注册时后者覆盖前者；Spring Bean 等显式注册会覆盖 ServiceLoader 发现的默认实现，
 * 并打印 warning 日志提示开发者。
 */
class ExternalFunctionTransportRegistry(
    transports: List<ExternalFunctionTransport> = emptyList(),
    useServiceLoader: Boolean = true
) {

    private val log = LoggerFactory.getLogger(ExternalFunctionTransportRegistry::class.java)

    private val registry: MutableMap<String, ExternalFunctionTransport> = ConcurrentHashMap()
    private val shutdown = AtomicBoolean(false)

    init {
        if (useServiceLoader) {
            loadFromServiceLoader().forEach { register(it, source = "ServiceLoader") }
        }
        transports.forEach { register(it, source = "explicit") }
    }

    /** 注册一个 transport，同协议重复注册时后者覆盖前者并打印警告。 */
    fun register(transport: ExternalFunctionTransport) {
        register(transport, source = "explicit")
    }

    private fun register(transport: ExternalFunctionTransport, source: String) {
        val protocol = transport.protocol().lowercase()
        val previous = registry.put(protocol, transport)
        if (previous != null) {
            log.warn {
                "ExternalFunctionTransport for protocol '$protocol' has been overridden by $source: " +
                    "${previous.javaClass.name} -> ${transport.javaClass.name}"
            }
        } else {
            log.info {
                "Registered ExternalFunctionTransport for protocol '$protocol' from $source: ${transport.javaClass.name}"
            }
        }
    }

    /** 注销指定协议的 transport。 */
    fun unregister(protocol: String): ExternalFunctionTransport? {
        val key = protocol.lowercase()
        val removed = registry.remove(key)
        removed?.let { log.info { "Unregistered ExternalFunctionTransport for protocol '$key'" } }
        return removed
    }

    /** 按协议名查找 transport。 */
    fun resolve(protocol: String?): ExternalFunctionTransport? {
        return protocol?.lowercase()?.let { registry[it] }
    }

    /** 当前已注册协议列表。 */
    fun protocols(): Set<String> = registry.keys.toSet()

    /** 是否包含指定协议的 transport。 */
    fun contains(protocol: String?): Boolean = protocol != null && registry.containsKey(protocol.lowercase())

    /** 当前已注册 transport 数量。 */
    fun size(): Int = registry.size

    /**
     * 按当前已加载的函数配置预建远程端点。
     *
     * 将配置按 protocol 分组后分发给对应 transport 的 [ExternalFunctionTransport.prepare]。
     * 通常在服务启动或配置中心全量推送后调用一次。
     */
    fun prepare(configs: List<ExternalFunctionConfig>) {
        if (shutdown.get()) {
            log.warn { "Registry is shutdown, skipping prepare for ${configs.size} config(s)" }
            return
        }
        configs.groupBy { it.protocol.lowercase() }
            .forEach { (protocol, protocolConfigs) ->
                val transport = registry[protocol]
                if (transport != null) {
                    log.info { "Preparing $protocol transport with ${protocolConfigs.size} config(s)" }
                    transport.prepare(protocolConfigs)
                } else {
                    log.debug { "No transport registered for protocol '$protocol', skipping prepare" }
                }
            }
    }

    /**
     * 优雅停机：遍历所有已注册 transport 调用 [ExternalFunctionTransport.shutdown]，
     * 并清空注册表，释放远程端点资源。
     *
     * 幂等：重复调用不会触发二次 shutdown。
     */
    fun shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            log.debug { "ExternalFunctionTransportRegistry already shutdown, skipping" }
            return
        }
        val transports = registry.values.toList()
        registry.clear()
        transports.forEach { transport ->
            try {
                log.info { "Shutting down external function transport: ${transport.protocol()} (${transport.javaClass.name})" }
                transport.shutdown()
            } catch (ex: Exception) {
                log.warn(ex) { "Failed to shutdown transport ${transport.protocol()} (${transport.javaClass.name})" }
            }
        }
        log.info { "ExternalFunctionTransportRegistry shutdown completed" }
    }

    /** 当前 registry 是否已停机。 */
    fun isShutdown(): Boolean = shutdown.get()

    companion object {

        /** 通过 ServiceLoader 加载所有可用的 ExternalFunctionTransport 实现。 */
        @JvmStatic
        fun loadFromServiceLoader(): List<ExternalFunctionTransport> {
            return ServiceLoader.load(ExternalFunctionTransport::class.java).toList()
        }
    }
}
