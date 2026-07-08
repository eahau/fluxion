/**
 * Spring Boot auto-configuration for the gRPC external-function transport.
 *
 * Activates only when `io.grpc.stub.AbstractStub` is on the classpath – a
 * reliable signal that the application already ships gRPC runtime jars and
 * can tolerate the extra reflection/netty dependencies the transport uses.
 */
package com.fluxion.external.function.grpc.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.external.function.grpc.GrpcExternalFunctionTransport
import io.grpc.stub.AbstractStub
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * Registers [GrpcExternalFunctionTransport] when gRPC stub classes are on
 * the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(AbstractStub::class)
class GrpcExternalFunctionAutoConfiguration {

    @Bean
    fun grpcExternalFunctionTransport(): ExternalFunctionTransport =
        GrpcExternalFunctionTransport()
}
