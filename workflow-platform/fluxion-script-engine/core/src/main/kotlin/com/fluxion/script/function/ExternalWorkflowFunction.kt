package com.fluxion.script.function

import com.fluxion.core.exception.WorkflowNodeException
import com.fluxion.core.function.WorkflowFunction
import com.fluxion.core.model.NodeInput
import com.fluxion.core.value.FunctionResult
import com.fluxion.outbound.OutboundConfig
import com.fluxion.outbound.OutboundConfigParser
import com.fluxion.outbound.OutboundRequest
import com.fluxion.outbound.OutboundTransportRegistry

/**
 * DAG-node wrapper for outbound "external" (HTTP / gRPC / Dubbo …) calls.
 *
 * The runtime-core deliberately knows nothing about any outbound transport
 * — it only exposes a pluggable `OutboundTransport` SPI. This class
 * is the bridge between that SPI and the DAG engine: it resolves the
 * correct transport for a given function's declared `protocol`, applies an
 * optional input-field mapping (`paramMapping`), builds the
 * [OutboundRequest] and invokes the transport.
 *
 * Two construction paths exist:
 * - Primary: direct object instantiation with a pre-parsed
 *   [OutboundConfig] (used by `FunctionConfigApplier` which already
 *   did validation while reading the config snapshot).
 * - Convenience: raw `Map<String, Any>` config that gets parsed through
 *   [OutboundConfigParser] on construction (used in tests and when
 *   functions are registered imperatively).
 */
class ExternalWorkflowFunction(
    private val name: String,
    private val config: OutboundConfig,
    private val transportRegistry: OutboundTransportRegistry,
    private val paramSchema: String? = null,
    private val outputSchema: String? = null,
    private val description: String? = null
) : WorkflowFunction<Any?> {

    /** Registered function reference (e.g. `external:userService.getUser`). */
    override val functionName: String = name

    /**
     * Convenience constructor — parses a raw config map through
     * [OutboundConfigParser] before delegating to the primary ctor.
     */
    constructor(
        name: String,
        configMap: Map<String, Any>?,
        transportRegistry: OutboundTransportRegistry,
        paramSchema: String? = null,
        outputSchema: String? = null,
        description: String? = null
    ) : this(
        name = name,
        config = OutboundConfigParser.parse(configMap),
        transportRegistry = transportRegistry,
        paramSchema = paramSchema,
        outputSchema = outputSchema,
        description = description
    )

    /**
     * Execute the external call.
     *
     * Pipeline:
     * 1. Resolve transport by `config.protocol` (fail-fast if unregistered).
     * 2. Optionally apply `paramMapping` to the DAG input — map entries are
     *    `targetField -> sourceDotPath` (e.g. `"city" -> "user.address.city"`).
     *    If no mapping is declared, `directInput` is passed as-is.
     * 3. Wrap in [OutboundRequest] and delegate to the transport.
     * 4. Check `response.success`; on failure surface via
     *    [WorkflowNodeException] so the DAG engine can route to a
     *    compensation / error handler instead of NPE-ing on a null output.
     */
    override fun apply(input: NodeInput): FunctionResult<Any?> {
        val transport = transportRegistry.resolve(config.protocol)
            ?: throw WorkflowNodeException(
                name,
                UnsupportedOperationException(
                    "No OutboundTransport registered for protocol [${config.protocol}]. " +
                        "Registered protocols: ${transportRegistry.protocols()}"
                )
            )

        val mapping = config.paramMapping
        val requestInput = if (mapping.isNullOrEmpty()) {
            input.directInput
        } else {
            mapInput(mapping, input.directInput)
        }

        val request = OutboundRequest(
            config = config,
            input = requestInput,
            functionRef = name,
            description = description
        )

        val response = try {
            transport.invoke(request)
        } catch (ex: Exception) {
            throw WorkflowNodeException(name, ex)
        }

        if (!response.success) {
            throw WorkflowNodeException(
                name,
                RuntimeException(
                    "External function invocation failed: [${response.errorCode}] ${response.errorMessage}"
                )
            )
        }

        return FunctionResult.success(response.output)
    }

    /**
     * Apply `paramMapping` — converts from DAG's input shape to the remote
     * service's expected request shape.
     *
     * For each `targetKey=dotPath` entry, walks the source map by splitting
     * the dot path and indexing into nested maps at each level. Missing
     * intermediate objects short-circuit to null (matching JSON-path
     * semantics for missing leaves).
     */
    private fun mapInput(mapping: Map<String, String>, source: Any?): Map<String, Any?> {
        val srcMap = source as? Map<*, *> ?: return emptyMap()
        return mapping.mapValues { (_, path) -> getByPath(srcMap, path) }
    }

    /** Walk a dot-separated path through nested maps, returning the leaf value or null. */
    @Suppress("UNCHECKED_CAST")
    private fun getByPath(source: Map<*, *>, path: String): Any? {
        val parts = path.split('.')
        var current: Any? = source
        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part) ?: return null
        }
        return current
    }
}
