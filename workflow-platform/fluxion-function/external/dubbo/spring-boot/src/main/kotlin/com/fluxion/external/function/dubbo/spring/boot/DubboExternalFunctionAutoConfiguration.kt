/**
 * Spring Boot auto-configuration for the Dubbo external-function transport.
 *
 * Activates only when `org.apache.dubbo.rpc.service.GenericService` is
 * present on the classpath (i.e. the operator pulled in the Dubbo starter
 * and runtime libraries). The exposed bean implements the common
 * [ExternalFunctionTransport] interface so the generic registry picks it
 * up by protocol name without further coupling.
 */
package com.fluxion.external.function.dubbo.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.external.function.dubbo.DubboExternalFunctionTransport
import org.apache.dubbo.rpc.service.GenericService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * Registers [DubboExternalFunctionTransport] when Dubbo generic service
 * classes are available.
 */
@AutoConfiguration
@ConditionalOnClass(GenericService::class)
class DubboExternalFunctionAutoConfiguration {

    @Bean
    fun dubboExternalFunctionTransport(): ExternalFunctionTransport =
        DubboExternalFunctionTransport()
}
