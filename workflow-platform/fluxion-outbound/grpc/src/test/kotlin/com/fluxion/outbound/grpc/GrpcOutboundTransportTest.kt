package com.fluxion.outbound.grpc
import com.fluxion.outbound.OutboundConfig
import com.fluxion.outbound.OutboundRequest
import com.fluxion.external.function.grpc.test.GreeterGrpc
import com.fluxion.external.function.grpc.test.GreeterProto
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.protobuf.services.ProtoReflectionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
 */
class GrpcOutboundTransportTest {
    private val transport = GrpcOutboundTransport()
    private var server: Server? = null
    @BeforeEach
    fun setUp() {
        server = ServerBuilder.forPort(0)
            .addService(GreeterService())
            .addService(ProtoReflectionService.newInstance())
            .intercept(TenantInterceptor)
            .build()
            .start()
    }
    @AfterEach
    fun tearDown() {
        server?.shutdownNow()
        server?.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
    }
    @Test
    fun `protocol returns grpc`() {
        assertThat(transport.protocol()).isEqualTo("grpc")
    }
    @Test
    fun `missing service returns config error`() {
        val request = request(config = OutboundConfig(protocol = "grpc", service = null, method = "SayHello"))
        val response = transport.invoke(request)
        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("GRPC_CONFIG_ERROR")
        assertThat(response.errorMessage).contains("service is required")
    }
    @Test
    fun `missing endpoint returns config error`() {
        val request = request(
            config = OutboundConfig(protocol = "grpc", service = "example.Greeter", method = "SayHello", endpoint = null)
        )
        val response = transport.invoke(request)
        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("GRPC_CONFIG_ERROR")
        assertThat(response.errorMessage).contains("endpoint is required")
    }
    @Test
    fun `reflection based unary call`() {
        val response = transport.invoke(
            request(
                config = OutboundConfig(
                    protocol = "grpc",
                    service = "example.Greeter",
                    method = "SayHello",
                    endpoint = "127.0.0.1:${server!!.port}"
                ),
                input = mapOf("name" to "Fluxion")
            )
        )
        assertThat(response.success).isTrue()
        @Suppress("UNCHECKED_CAST")
        val output = response.output as Map<String, Any>
        assertThat(output["message"]).isEqualTo("Hello, Fluxion")
    }
    @Test
    fun `reflection based call with multiple fields`() {
        val response = transport.invoke(
            request(
                config = OutboundConfig(
                    protocol = "grpc",
                    service = "example.Greeter",
                    method = "Add",
                    endpoint = "127.0.0.1:${server!!.port}"
                ),
                input = mapOf("a" to 10, "b" to 32)
            )
        )
        assertThat(response.success).isTrue()
        @Suppress("UNCHECKED_CAST")
        val output = response.output as Map<String, Any>
        assertThat(output["result"]).isEqualTo(42)
    }
    @Test
    fun `metadata is sent to server`() {
        val response = transport.invoke(
            request(
                config = OutboundConfig(
                    protocol = "grpc",
                    service = "example.Greeter",
                    method = "SayHello",
                    endpoint = "127.0.0.1:${server!!.port}",
                    headers = mapOf("x-tenant" to "acme")
                ),
                input = mapOf("name" to "TenantCheck")
            )
        )
        assertThat(response.success).isTrue()
        @Suppress("UNCHECKED_CAST")
        val output = response.output as Map<String, Any>
        assertThat(output["message"]).isEqualTo("Hello, TenantCheck (acme)")
    }
    @Test
    fun `shutdown closes channels and releases caches`() {
        transport.invoke(
            request(
                config = OutboundConfig(
                    protocol = "grpc",
                    service = "example.Greeter",
                    method = "SayHello",
                    endpoint = "127.0.0.1:${server!!.port}"
                ),
                input = mapOf("name" to "Fluxion")
            )
        )
        transport.shutdown()
        val response = transport.invoke(
            request(
                config = OutboundConfig(
                    protocol = "grpc",
                    service = "example.Greeter",
                    method = "SayHello",
                    endpoint = "127.0.0.1:${server!!.port}"
                ),
                input = mapOf("name" to "Fluxion")
            )
        )
        assertThat(response.success).isFalse()
        assertThat(response.errorCode).isEqualTo("GRPC_CONFIG_ERROR")
        assertThat(response.errorMessage).isEqualTo("gRPC transport is shutdown")
    }
    @Test
    fun `shutdown is idempotent`() {
        transport.shutdown()
        transport.shutdown()
        
    }
    private fun request(config: OutboundConfig, input: Any? = null): OutboundRequest =
        OutboundRequest(
            config = config,
            input = input,
            functionRef = "test:grpc"
        )
    private class GreeterService : GreeterGrpc.GreeterImplBase() {
        override fun sayHello(
            request: GreeterProto.HelloRequest,
            responseObserver: io.grpc.stub.StreamObserver<GreeterProto.HelloReply>
        ) {
            val tenant = TENANT_CONTEXT_KEY.get() ?: ""
            val name = request.name
            val message = if (tenant.isNotBlank()) "Hello, $name ($tenant)" else "Hello, $name"
            responseObserver.onNext(GreeterProto.HelloReply.newBuilder().setMessage(message).build())
            responseObserver.onCompleted()
        }
        override fun add(
            request: GreeterProto.AddRequest,
            responseObserver: io.grpc.stub.StreamObserver<GreeterProto.AddReply>
        ) {
            responseObserver.onNext(GreeterProto.AddReply.newBuilder().setResult(request.a + request.b).build())
            responseObserver.onCompleted()
        }
    }
    companion object {
        private val TENANT_CONTEXT_KEY: Context.Key<String> = Context.key("x-tenant")
        private val TENANT_METADATA_KEY: Metadata.Key<String> = Metadata.Key.of("x-tenant", Metadata.ASCII_STRING_MARSHALLER)
        private object TenantInterceptor : ServerInterceptor {
            override fun <ReqT : Any, RespT : Any> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>
            ): ServerCall.Listener<ReqT> {
                val tenant = headers.get(TENANT_METADATA_KEY) ?: ""
                val context = Context.current().withValue(TENANT_CONTEXT_KEY, tenant)
                return Contexts.interceptCall(context, call, headers, next)
            }
        }
    }
}
