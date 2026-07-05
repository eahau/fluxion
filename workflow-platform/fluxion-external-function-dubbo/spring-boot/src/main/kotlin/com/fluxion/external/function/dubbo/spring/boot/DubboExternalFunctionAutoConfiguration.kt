package com.fluxion.external.function.dubbo.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.external.function.dubbo.DubboExternalFunctionTransport
import org.apache.dubbo.rpc.service.GenericService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * Dubbo 外部函数 Transport 自动装配。
 *
 * 当 classpath 存在 Dubbo GenericService 时自动注册 [DubboExternalFunctionTransport]。
 */
@AutoConfiguration
@ConditionalOnClass(GenericService::class)
class DubboExternalFunctionAutoConfiguration {

    @Bean
    fun dubboExternalFunctionTransport(): ExternalFunctionTransport =
        DubboExternalFunctionTransport()
}
