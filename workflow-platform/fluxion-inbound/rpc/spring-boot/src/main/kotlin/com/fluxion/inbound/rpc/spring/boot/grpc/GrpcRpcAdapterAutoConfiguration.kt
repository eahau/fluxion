package com.fluxion.inbound.rpc.spring.boot.grpc

import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.inbound.rpc.grpc.GrpcInboundTriggerMeta
import com.fluxion.inbound.rpc.grpc.GrpcMetadataInterceptor
import com.fluxion.inbound.spi.InboundRouter
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaRegistry
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.slf4j.LoggerFactory
import org.slf4j.debug
import org.slf4j.info
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(
    name = [
        "net.devh.boot.grpc.server.service.GrpcService",
        "com.fluxion.inbound.rpc.grpc.proto.WorkflowServiceGrpc"
    ]
)
@ConditionalOnProperty(name = ["workflow.rpc.grpc.enabled"], havingValue = "true", matchIfMissing = true)
class GrpcRpcAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun grpcInboundTriggerMeta(): TriggerFunctionMeta = GrpcInboundTriggerMeta()

    @Bean
    @ConditionalOnMissingBean
    fun grpcWorkflowService(
        inboundRouter: InboundRouter,
        schemaRegistry: SchemaRegistry,
        @Autowired(required = false) providerRegistry: SchemaDataProviderRegistry? = null
    ): GrpcWorkflowService {
        log.info { "Registering GrpcWorkflowService" }
        return GrpcWorkflowService(inboundRouter, schemaRegistry, providerRegistry)
    }

    @Bean
    @GrpcGlobalServerInterceptor
    fun grpcMetadataInterceptor(): GrpcMetadataInterceptor {
        log.debug { "Registering global GrpcMetadataInterceptor" }
        return GrpcMetadataInterceptor()
    }
}
