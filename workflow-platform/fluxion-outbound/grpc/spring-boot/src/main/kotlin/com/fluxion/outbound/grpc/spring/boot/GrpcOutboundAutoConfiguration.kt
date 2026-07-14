/**
 * Spring Boot auto-configuration for the gRPC external-function transport.
 *
 * Activates only when `io.grpc.stub.AbstractStub` is on the classpath – a
 * reliable signal that the application already ships gRPC runtime jars and
 * can tolerate the extra reflection/netty dependencies the transport uses.
 */
package com.fluxion.outbound.grpc.spring.boot

import com.fluxion.outbound.OutboundTransport
import com.fluxion.outbound.grpc.GrpcOutboundTransport
import io.grpc.stub.AbstractStub
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * Registers [GrpcOutboundTransport] when gRPC stub classes are on
 * the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(AbstractStub::class)
class GrpcOutboundAutoConfiguration {

    @Bean
    fun grpcOutboundTransport(): OutboundTransport =
        GrpcOutboundTransport()
}
