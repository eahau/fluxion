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
 * Spring Boot auto-configuration for both RPC transports (Dubbo + gRPC).
 *
 * The two stacks are kept in separate nested `@Configuration` classes so the
 * absence of one client library on the classpath doesn't prevent the other
 * from loading (thanks to ASM-based lazy class reading of `@Configuration`
 * in Spring Boot — `@ConditionalOnClass` on the nested class gates all of
 * its bean methods against class presence, not just the configuration class
 * annotation evaluation).
 */
@Configuration
class RpcAdapterAutoConfiguration {

    /**
     * Dubbo RPC binding — activates iff `@DubboService` is on the classpath AND
     * `workflow.rpc.dubbo.enabled=true` (defaults to true so apps that include
     * the Dubbo starter get the binding automatically).
     */
    @Configuration
    @ConditionalOnClass(name = ["org.apache.dubbo.config.annotation.DubboService"])
    @ConditionalOnProperty(name = ["workflow.rpc.dubbo.enabled"], havingValue = "true", matchIfMissing = true)
    class DubboConfiguration {

        /**
         * Register the Dubbo-annotated service bean — the Dubbo starter picks up
         * `@DubboService` beans automatically and exports them through the
         * configured protocol/registry.
         */
        @Bean
        @ConditionalOnMissingBean
        fun dubboWorkflowService(
            workflowRouter: WorkflowRouter
        ): DubboWorkflowService = DubboWorkflowService(workflowRouter)
    }

    /**
     * gRPC binding — activates iff the `@GrpcService` annotation is on the
     * classpath (i.e. app includes the `grpc-server-spring-boot-starter`) AND
     * `workflow.rpc.grpc.enabled=true` (default true).
     */
    @Configuration
    @ConditionalOnClass(name = ["net.devh.boot.grpc.server.service.GrpcService"])
    @ConditionalOnProperty(name = ["workflow.rpc.grpc.enabled"], havingValue = "true", matchIfMissing = true)
    class GrpcConfiguration {

        /**
         * Register the gRPC service bean. The grpc-server-spring-boot-starter
         * discovers beans annotated with `@GrpcService` and binds each to the
         * shared gRPC server's service registry.
         */
        @Bean
        @ConditionalOnMissingBean
        fun grpcWorkflowService(
            workflowRouter: WorkflowRouter,
            schemaRegistry: SchemaRegistry,
            @Autowired(required = false) providerRegistry: SchemaDataProviderRegistry? = null
        ): GrpcWorkflowService = GrpcWorkflowService(workflowRouter, schemaRegistry, providerRegistry)

        /**
         * Register the metadata-extracting server interceptor GLOBALLY so the
         * routing metadata (`x-workflow-id`, `x-service-key`, `trace-id` …)
         * placed in HTTP/2 headers by the caller is always captured into the
         * gRPC Context BEFORE any service method executes.
         */
        @Bean
        @GrpcGlobalServerInterceptor
        fun grpcMetadataInterceptor(): GrpcMetadataInterceptor = GrpcMetadataInterceptor()
    }
}
