package com.fluxion.external.function.grpc.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.external.function.grpc.GrpcExternalFunctionTransport
import io.grpc.stub.AbstractStub
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * gRPC 外部函数 Transport 自动装配。
 *
 * 当 classpath 存在 gRPC stub 时自动注册 [GrpcExternalFunctionTransport]。
 */
@AutoConfiguration
@ConditionalOnClass(AbstractStub::class)
class GrpcExternalFunctionAutoConfiguration {

    @Bean
    fun grpcExternalFunctionTransport(): ExternalFunctionTransport =
        GrpcExternalFunctionTransport()
}
