package com.fluxion.adapter.rpc.spring.boot

import com.fluxion.adapter.grpc.GrpcMetadataInterceptor
import com.fluxion.adapter.rpc.spring.boot.dubbo.DubboWorkflowService
import com.fluxion.adapter.rpc.spring.boot.grpc.GrpcWorkflowService
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaRegistry
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RPC 适配器 Spring Boot 自动装配
 */
@Configuration
class RpcAdapterAutoConfiguration {

    /**
     * Dubbo 服务暴露：classpath 存在 DubboService 注解时生效
     */
    @Configuration
    @ConditionalOnClass(name = ["org.apache.dubbo.config.annotation.DubboService"])
    @ConditionalOnProperty(name = ["workflow.rpc.dubbo.enabled"], havingValue = "true", matchIfMissing = true)
    class DubboConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun dubboWorkflowService(
            workflowRouter: WorkflowRouter
        ): DubboWorkflowService = DubboWorkflowService(workflowRouter)
    }

    /**
     * gRPC 服务暴露：classpath 存在 GrpcService 注解时生效
     */
    @Configuration
    @ConditionalOnClass(name = ["net.devh.boot.grpc.server.service.GrpcService"])
    @ConditionalOnProperty(name = ["workflow.rpc.grpc.enabled"], havingValue = "true", matchIfMissing = true)
    class GrpcConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun grpcWorkflowService(
            workflowRouter: WorkflowRouter,
            schemaRegistry: SchemaRegistry,
            @Autowired(required = false) providerRegistry: SchemaDataProviderRegistry? = null
        ): GrpcWorkflowService = GrpcWorkflowService(workflowRouter, schemaRegistry, providerRegistry)

        /**
         * gRPC Metadata 拦截器：从 HTTP/2 headers 提取 metadata 放入 Context
         */
        @Bean
        @GrpcGlobalServerInterceptor
        fun grpcMetadataInterceptor(): GrpcMetadataInterceptor = GrpcMetadataInterceptor()
    }
}
