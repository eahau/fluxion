/**
 * Structured configuration for one `EXTERNAL` workflow function.
 *
 * The generic shape covers every concrete transport (HTTP/gRPC/Dubbo)
 * because all three share the same high-level knobs: protocol selector,
 * service/method routing, endpoint overrides, timeout and headers.
 * Transport-specific knobs that do not belong on the shared surface (Dubbo
 * `version`/`group`/`parameterTypes`, gRPC `useTls`) are intentionally
 * deferred to the [extras] map so new transports can add config without
 * changing this data class.
 *
 * The parser in [ExternalFunctionConfigParser] handles backward-compatible
 * flattening so old DSLs that nested config under `externalService` still
 * materialise as a top-level instance.
 */
package com.fluxion.core.function.external

/**
 * Immutable configuration for a single external-function declaration.
 *
 * @property protocol lowercase transport key used to route via the registry
 * @property service target selector – URL for HTTP, FQCN for Dubbo, full
 *   service name for gRPC
 * @property method operation selector – HTTP verb, Dubbo Java method name,
 *   gRPC method short name
 * @property endpoint optional explicit address used to bypass a registry
 * @property timeoutMs per-call timeout; 0 means the transport uses its own default
 * @property headers transport-level metadata (HTTP headers, Dubbo attachments,
 *   gRPC Metadata)
 * @property paramMapping sparse DSL mapping node-input paths to formal
 *   parameter slots; semantics are transport specific
 * @property extras open-ended map for transport-specific knobs
 */
data class ExternalFunctionConfig(
    val protocol: String = "http",
    val service: String? = null,
    val method: String? = null,
    val endpoint: String? = null,
    val timeoutMs: Long = 0,
    val headers: Map<String, String>? = null,
    val paramMapping: Map<String, String>? = null,
    val extras: Map<String, Any>? = null
) {
    /** Convenience accessor returning an extras value as string, or `null`. */
    fun extraString(key: String): String? = extras?.get(key)?.toString()

    /** Convenience accessor returning an extras value as a string list, or `null`. */
    @Suppress("UNCHECKED_CAST")
    fun extraStringList(key: String): List<String>? = extras?.get(key) as? List<String>
}
