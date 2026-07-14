/**
 * Spring Boot auto-configuration for the Dubbo external-function transport.
 *
 * Activates only when `org.apache.dubbo.rpc.service.GenericService` is
 * present on the classpath (i.e. the operator pulled in the Dubbo starter
 * and runtime libraries). The exposed bean implements the common
 * [OutboundTransport] interface so the generic registry picks it
 * up by protocol name without further coupling.
 */
package com.fluxion.outbound.dubbo.spring.boot

import com.fluxion.outbound.OutboundTransport
import com.fluxion.outbound.dubbo.DubboOutboundTransport
import org.apache.dubbo.rpc.service.GenericService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * Registers [DubboOutboundTransport] when Dubbo generic service
 * classes are available.
 */
@AutoConfiguration
@ConditionalOnClass(GenericService::class)
class DubboOutboundAutoConfiguration {

    @Bean
    fun dubboOutboundTransport(): OutboundTransport =
        DubboOutboundTransport()
}
