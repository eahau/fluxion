package com.fluxion.core.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import org.slf4j.*

/**
 * 外部函数 Transport 注册器。
 *
 * 参考 [com.fluxion.builtin.config.BuiltinFunctionRegistrar] 的实现风格：
 * Spring Boot 启动阶段收集所有 [ExternalFunctionTransport] Bean，统一注册到
 * [ExternalFunctionTransportRegistry]，实现与核心注册逻辑解耦，并支持运行时覆盖/扩展。
 */
class ExternalFunctionTransportRegistrar(
    registry: ExternalFunctionTransportRegistry,
    transports: List<ExternalFunctionTransport>
) {

    init {
        var count = 0
        transports.forEach { transport ->
            registry.register(transport)
            count++
        }
        log.info { "Registered $count external function transports: ${registry.protocols()}" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ExternalFunctionTransportRegistrar::class.java)
    }
}
