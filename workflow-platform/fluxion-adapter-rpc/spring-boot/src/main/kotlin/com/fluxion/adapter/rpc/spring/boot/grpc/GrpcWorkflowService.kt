package com.fluxion.adapter.rpc.spring.boot.grpc

import com.fluxion.adapter.grpc.GrpcWorkflowServiceImpl
import com.fluxion.adapter.grpc.proto.WorkflowServiceGrpc
import com.fluxion.adapter.grpc.proto.WorkflowStreamEvent
import com.fluxion.adapter.spi.WorkflowRouter
import com.fluxion.schema.api.SchemaDataProviderRegistry
import com.fluxion.schema.api.SchemaRegistry
import com.google.protobuf.Any
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.beans.factory.annotation.Autowired

/**
 * gRPC 工作流服务 Spring Boot 暴露
 *
 * 零 Spring 实现参见 workflow-adapter-rpc-grpc 的 GrpcWorkflowServiceImpl
 */
@GrpcService
open class GrpcWorkflowService(
    workflowRouter: WorkflowRouter,
    schemaRegistry: SchemaRegistry,
    @Autowired(required = false) providerRegistry: SchemaDataProviderRegistry? = null
) : WorkflowServiceGrpc.WorkflowServiceImplBase() {

    private val delegate = GrpcWorkflowServiceImpl(workflowRouter, schemaRegistry, providerRegistry)

    override fun execute(payload: Any, responseObserver: StreamObserver<Any>) =
        delegate.execute(payload, responseObserver)

    override fun executeStream(payload: Any, responseObserver: StreamObserver<WorkflowStreamEvent>) =
        delegate.executeStream(payload, responseObserver)
}
