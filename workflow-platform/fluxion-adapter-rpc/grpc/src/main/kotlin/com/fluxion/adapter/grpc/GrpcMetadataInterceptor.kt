package com.fluxion.adapter.grpc

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ForwardingServerCallListener

/**
 * gRPC Metadata 拦截器
 *
 * 从 gRPC Metadata（HTTP/2 headers）中提取所有键值对，
 * 放入 gRPC Context，供业务代码读取。
 *
 * 这样 proto 中无需定义 metadata 字段，直接复用 gRPC 原生机制。
 */
class GrpcMetadataInterceptor : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        // 从 headers 中提取所有键值对
        val metadataMap = mutableMapOf<String, String>()
        headers.keys().forEach { key ->
            if (!key.endsWith("-bin")) { // 跳过二进制字段
                val value = headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
                if (value != null) {
                    metadataMap[key] = value
                }
            }
        }

        // 放入 Context
        val ctx = Context.current().withValue(METADATA_CONTEXT_KEY, metadataMap)
        return Contexts.interceptCall(ctx, call, headers, next)
    }

    companion object {
        /** Context Key 用于存储 metadata map */
        val METADATA_CONTEXT_KEY: Context.Key<Map<String, String>> =
            Context.key("grpc-metadata")
    }
}
