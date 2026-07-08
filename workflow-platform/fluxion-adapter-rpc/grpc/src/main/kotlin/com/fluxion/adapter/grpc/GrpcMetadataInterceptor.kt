package com.fluxion.adapter.grpc

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.ForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

/**
 * gRPC server interceptor that extracts ALL ASCII-valued metadata (the
 * caller's HTTP/2 headers) into the gRPC `Context` so the service method
 * can read them without the proto schema needing a "metadata" field.
 *
 * Binary metadata entries (`*-bin` suffix) are deliberately SKIPPED — we only
 * care about text headers (trace-id, x-workflow-id, x-service-key, tenant-id
 * …). This avoids polluting the downstream `UnifiedRequest.headers` map with
 * binary Protobuf payloads that are already being parsed separately.
 *
 * Captured metadata is keyed under [METADATA_CONTEXT_KEY] — the downstream
 * service implementation reads it out via `METADATA_CONTEXT_KEY.get()`.
 */
class GrpcMetadataInterceptor : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val metadataMap = mutableMapOf<String, String>()
        headers.keys().forEach { key ->
            // Skip binary metadata entries — they hold raw bytes we cannot
            // safely interpret as UTF-8 strings anyway.
            if (!key.endsWith("-bin")) {
                val value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
                if (value != null) {
                    metadataMap[key] = value
                }
            }
        }

        // Attach the captured map to the current Context. Contexts.interceptCall
        // is the official gRPC mechanism — it sets the Context for the call
        // handler thread (including the onMessage / onHalfClose invocations).
        val ctx = Context.current().withValue(METADATA_CONTEXT_KEY, metadataMap)
        return Contexts.interceptCall(ctx, call, headers, next)
    }

    companion object {
        /** Context.Key holding the captured per-call text-headers map. */
        val METADATA_CONTEXT_KEY: Context.Key<Map<String, String>> =
            Context.key("grpc-metadata")
    }
}
