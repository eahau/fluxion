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
 * Spring Boot exposed gRPC service — `@GrpcService` (from `net.devh:grpc-server-spring-boot-starter`)
 * auto-binds this bean into the gRPC server's service registry on the configured port.
 *
 * Business logic is delegated to the framework-agnostic [GrpcWorkflowServiceImpl]
 * (keeps the transport logic unit-testable without a Spring context).
 *
 * Two gRPC endpoints are exposed:
 * - `execute`      — single request / single protobuf-Any response.
 * - `executeStream` — single request / streaming `WorkflowStreamEvent` response
 *                     (one event per DAG node completion, terminated by a
 *                     `WORKFLOW_DONE` envelope carrying the final result).
 *
 * @param workflowRouter Protocol-agnostic router provided by runtime-core.
 * @param schemaRegistry Shared registry for PROTOBUF + JSON schema definitions.
 * @param providerRegistry Optional extended-schema provider (adds custom
 *                         getters/setters on SchemaBackedMap — used only when
 *                         the schema module ships advanced plugins).
 */
@GrpcService
open class GrpcWorkflowService(
    workflowRouter: WorkflowRouter,
    schemaRegistry: SchemaRegistry,
    @Autowired(required = false) providerRegistry: SchemaDataProviderRegistry? = null
) : WorkflowServiceGrpc.WorkflowServiceImplBase() {

    private val delegate = GrpcWorkflowServiceImpl(workflowRouter, schemaRegistry, providerRegistry)

    /** Unary execute — one request → one protobuf-Any response. */
    override fun execute(payload: Any, responseObserver: StreamObserver<Any>) =
        delegate.execute(payload, responseObserver)

    /** Stream execute — server-side streaming of DAG node progress events. */
    override fun executeStream(payload: Any, responseObserver: StreamObserver<WorkflowStreamEvent>) =
        delegate.executeStream(payload, responseObserver)
}
