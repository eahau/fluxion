/**
 * gRPC-backed external function transport.
 *
 * Uses the gRPC **Server Reflection** protocol to dynamically discover the
 * service and method descriptor at call time; callers therefore do not need
 * to ship the generated stub jars with their workers. The flow is:
 * 1. Open (or reuse) a `ManagedChannel` to the configured `endpoint`.
 * 2. Resolve the file descriptor containing the target service via the
 *    reflection stub.
 * 3. Build a `DynamicMessage` from the engine input using JSON merge.
 * 4. Issue a blocking unary call and convert the `DynamicMessage` response
 *    back to a generic `Map<String, Any?>` via the JSON printer.
 *
 * Example `ExternalFunctionConfig` JSON:
 * ```json
 * {
 *   "protocol": "grpc",
 *   "service": "com.example.Greeter",
 *   "method": "SayHello",
 *   "endpoint": "127.0.0.1:50051",
 *   "timeoutMs": 5000,
 *   "headers": { "x-tenant": "abc" },
 *   "extras": { "useTls": false }
 * }
 * ```
 *
 * The target server MUST have Server Reflection enabled (typically provided
 * by the `grpc-services` artifact). We cache both channels and descriptors
 * aggressively; cache entries are released through
 * [ExternalFunctionTransportLifecycle] on shutdown.
 */
package com.fluxion.external.function.grpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fluxion.core.function.external.ExternalFunctionConfig
import com.fluxion.core.function.external.ExternalFunctionRequest
import com.fluxion.core.function.external.ExternalFunctionResponse
import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportLifecycle
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DynamicMessage
import com.google.protobuf.util.JsonFormat
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.StatusRuntimeException
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.stub.ClientCalls
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reflection-driven gRPC transport; caches `ManagedChannel` and method
 * `Descriptor` instances to avoid repeated reflection round-trips.
 */
class GrpcExternalFunctionTransport : ExternalFunctionTransport {

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5_000L
    }

    private val log = LoggerFactory.getLogger(javaClass)
    private val lifecycle = ExternalFunctionTransportLifecycle()
    private val channelCache = ConcurrentHashMap<String, ManagedChannel>()
    private val descriptorCache = ConcurrentHashMap<String, Descriptors.MethodDescriptor>()
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    init {
        lifecycle.onClearCache("grpc-descriptor-cache") { descriptorCache.clear() }
        lifecycle.onClearCache("grpc-channel-cache") { channelCache.clear() }
    }

    override fun protocol(): String = "grpc"

    override fun prepare(configs: List<ExternalFunctionConfig>) {
        if (lifecycle.isShutdown()) {
            log.warn { "gRPC transport is shutdown, skipping prepare for ${configs.size} config(s)" }
            return
        }
        configs.forEach { config ->
            val service = config.service ?: return@forEach
            val method = config.method ?: return@forEach
            val endpoint = config.endpoint ?: return@forEach
            val timeoutMs = if (config.timeoutMs > 0) config.timeoutMs else DEFAULT_TIMEOUT_MS

            val channel = channelCache.computeIfAbsent(buildChannelKey(config)) { buildChannel(config) }
            val fullMethodName = "$service/$method"
            descriptorCache.computeIfAbsent("$endpoint#$fullMethodName") {
                resolveMethodDescriptor(channel, service, method, timeoutMs)
            }
        }
        log.info { "gRPC transport prepared ${configs.size} channel(s) / descriptor(s)" }
    }

    override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse {
        if (lifecycle.isShutdown()) {
            return errorResponse("gRPC transport is shutdown")
        }
        val config = request.config
        val service = config.service
            ?: return errorResponse("service is required for grpc protocol")
        val method = config.method
            ?: return errorResponse("method is required for grpc protocol")
        val endpoint = config.endpoint
            ?: return errorResponse("endpoint is required for grpc protocol")

        val fullMethodName = "$service/$method"
        val timeoutMs = if (config.timeoutMs > 0) config.timeoutMs else DEFAULT_TIMEOUT_MS

        return try {
            val channel = channelCache.computeIfAbsent(buildChannelKey(config)) { buildChannel(config) }
            val methodDescriptor = descriptorCache.computeIfAbsent("$endpoint#$fullMethodName") {
                resolveMethodDescriptor(channel, service, method, timeoutMs)
            }

            val requestMessage = buildRequestMessage(methodDescriptor, request.input)
            val responseMessage = performUnaryCall(
                channel,
                methodDescriptor,
                requestMessage,
                buildMetadata(config.headers),
                timeoutMs
            )

            ExternalFunctionResponse(output = convertMessage(responseMessage))
        } catch (ex: StatusRuntimeException) {
            log.error(ex) { "gRPC invoke failed: [$fullMethodName] -> ${ex.status}" }
            ExternalFunctionResponse(
                output = null,
                success = false,
                errorCode = "GRPC_STATUS_${ex.status.code}",
                errorMessage = ex.status.description ?: ex.message ?: ex.javaClass.name
            )
        } catch (ex: Exception) {
            log.error(ex) { "gRPC invoke failed: [$fullMethodName]" }
            ExternalFunctionResponse(
                output = null,
                success = false,
                errorCode = "GRPC_INVOKE_ERROR",
                errorMessage = ex.message ?: ex.javaClass.name
            )
        }
    }

    private fun buildChannel(config: ExternalFunctionConfig): ManagedChannel {
        val endpoint = config.endpoint ?: throw IllegalArgumentException("endpoint is required")
        val useTls = config.extras?.get("useTls")?.toString()?.toBooleanStrictOrNull() ?: false

        val builder = ManagedChannelBuilder.forTarget(endpoint)
            .let { if (useTls) it.useTransportSecurity() else it.usePlaintext() }
        val channel = builder.build()
        lifecycle.onShutdown("grpc-channel:`$endpoint") { shutdownChannelGracefully(channel) }
        return channel
    }

    private fun shutdownChannelGracefully(channel: ManagedChannel) {
        try {
            if (!channel.isShutdown) {
                channel.shutdown()
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow()
                    channel.awaitTermination(1, TimeUnit.SECONDS)
                }
            }
        } catch (ex: Exception) {
            log.warn(ex) { "Error while shutting down gRPC channel" }
            try {
                channel.shutdownNow()
            } catch (_: Exception) {
                // Double failure during shutdown is rare and unrecoverable;
                // the JVM is already exiting so swallow rather than propagate.
            }
        }
    }

    private fun buildChannelKey(config: ExternalFunctionConfig): String {
        val useTls = config.extras?.get("useTls")?.toString()?.toBooleanStrictOrNull() ?: false
        return "${config.endpoint}#tls=$useTls"
    }

    private fun resolveMethodDescriptor(
        channel: Channel,
        serviceName: String,
        methodName: String,
        timeoutMs: Long
    ): Descriptors.MethodDescriptor {
        val fileDescriptor = resolveFileDescriptor(channel, serviceName, timeoutMs)
        val serviceDescriptor = fileDescriptor.findServiceByName(serviceName.substringAfterLast('.'))
            ?: throw IllegalArgumentException("Service not found in reflection response: `$serviceName")
        return serviceDescriptor.methods.find { it.name == methodName }
            ?: throw IllegalArgumentException("Method not found: $methodName in service `$serviceName")
    }

    private fun resolveFileDescriptor(
        channel: Channel,
        serviceName: String,
        timeoutMs: Long
    ): Descriptors.FileDescriptor {
        val stub = ServerReflectionGrpc.newStub(channel)
        val latch = CountDownLatch(1)
        val responses = mutableListOf<ServerReflectionResponse>()
        val errorHolder = mutableListOf<Throwable>()

        val requestStream = stub.serverReflectionInfo(object : StreamObserver<ServerReflectionResponse> {
            override fun onNext(response: ServerReflectionResponse) {
                responses.add(response)
                if (response.hasFileDescriptorResponse()) {
                    latch.countDown()
                }
            }

            override fun onError(t: Throwable) {
                errorHolder.add(t)
                latch.countDown()
            }

            override fun onCompleted() {
                if (latch.count > 0) latch.countDown()
            }
        })

        requestStream.onNext(
            ServerReflectionRequest.newBuilder()
                .setFileContainingSymbol(serviceName)
                .build()
        )
        requestStream.onCompleted()

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("gRPC reflection request timed out after ${timeoutMs}ms")
        }
        if (errorHolder.isNotEmpty()) {
            throw IllegalStateException("gRPC reflection request failed", errorHolder.first())
        }

        val fileDescriptorResponse = responses.firstOrNull { it.hasFileDescriptorResponse() }?.fileDescriptorResponse
            ?: throw IllegalStateException("No FileDescriptorResponse received from gRPC reflection")

        val protoList = fileDescriptorResponse.fileDescriptorProtoList.map {
            DescriptorProtos.FileDescriptorProto.parseFrom(it)
        }
        return buildFileDescriptor(protoList)
    }

    private fun buildFileDescriptor(protoList: List<DescriptorProtos.FileDescriptorProto>): Descriptors.FileDescriptor {
        val protoByName = protoList.associateBy { it.name }
        val built = mutableMapOf<String, Descriptors.FileDescriptor>()

        fun build(name: String): Descriptors.FileDescriptor {
            built[name]?.let { return it }
            val proto = protoByName[name]
                ?: throw IllegalArgumentException("Missing FileDescriptorProto: `$name")
            val dependencies = proto.dependencyList.map { build(it) }
            val descriptor = Descriptors.FileDescriptor.buildFrom(proto, dependencies.toTypedArray())
            built[name] = descriptor
            return descriptor
        }

        protoList.forEach { build(it.name) }
        return built.values.lastOrNull()
            ?: throw IllegalStateException("No FileDescriptor built from reflection response")
    }

    private fun buildRequestMessage(
        methodDescriptor: Descriptors.MethodDescriptor,
        input: Any?
    ): DynamicMessage {
        val descriptor = methodDescriptor.inputType
        val builder = DynamicMessage.newBuilder(descriptor)
        if (input == null) return builder.build()

        val json = when (input) {
            is String -> input
            else -> objectMapper.writeValueAsString(input)
        }
        JsonFormat.parser().merge(json, builder)
        return builder.build()
    }

    private fun performUnaryCall(
        channel: Channel,
        methodDescriptor: Descriptors.MethodDescriptor,
        request: DynamicMessage,
        metadata: Metadata,
        timeoutMs: Long
    ): DynamicMessage {
        val grpcMethod = MethodDescriptor.newBuilder<DynamicMessage, DynamicMessage>(
            DynamicMessageMarshaller(methodDescriptor.inputType),
            DynamicMessageMarshaller(methodDescriptor.outputType)
        )
            .setFullMethodName("${methodDescriptor.service.fullName}/${methodDescriptor.name}")
            .setType(MethodDescriptor.MethodType.UNARY)
            .build()

        val interceptedChannel = ClientInterceptors.intercept(channel, MetadataInterceptor(metadata))
        return ClientCalls.blockingUnaryCall(
            interceptedChannel,
            grpcMethod,
            CallOptions.DEFAULT.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS),
            request
        )
    }

    private class MetadataInterceptor(private val metadata: Metadata) : ClientInterceptor {
        override fun <ReqT : Any, RespT : Any> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            callOptions: CallOptions,
            next: Channel
        ): ClientCall<ReqT, RespT> {
            return object : ClientCall<ReqT, RespT>() {
                private val delegate = next.newCall(method, callOptions)

                override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                    headers.merge(metadata)
                    delegate.start(responseListener, headers)
                }

                override fun request(numMessages: Int) = delegate.request(numMessages)
                override fun cancel(message: String?, cause: Throwable?) = delegate.cancel(message, cause)
                override fun halfClose() = delegate.halfClose()
                override fun sendMessage(message: ReqT) = delegate.sendMessage(message)
            }
        }
    }

    private fun buildMetadata(headers: Map<String, String>?): Metadata {
        val metadata = Metadata()
        headers?.forEach { (key, value) ->
            if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                // Binary headers intentionally skipped for now – callers use
                // ASCII metadata for auth/tracing which is the common case.
                return@forEach
            }
            metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        }
        return metadata
    }

    private fun convertMessage(message: DynamicMessage): Any? {
        if (message.allFields.isEmpty()) return null
        val json = JsonFormat.printer().print(message)
        return objectMapper.readValue(json, Any::class.java)
    }

    override fun shutdown() {
        val channelsBeforeShutdown = channelCache.size
        lifecycle.shutdown()
        log.info { "gRPC transport shutdown completed, $channelsBeforeShutdown channel(s) released" }
    }

    private fun errorResponse(message: String): ExternalFunctionResponse = ExternalFunctionResponse(
        output = null,
        success = false,
        errorCode = "GRPC_CONFIG_ERROR",
        errorMessage = message
    )

    private class DynamicMessageMarshaller(
        private val descriptor: Descriptors.Descriptor
    ) : MethodDescriptor.Marshaller<DynamicMessage> {
        override fun stream(value: DynamicMessage): InputStream = ByteArrayInputStream(value.toByteArray())
        override fun parse(stream: InputStream): DynamicMessage = DynamicMessage.parseFrom(descriptor, stream)
    }
}
