/**
 * Outbound Executor — central coordination interface for external function invocation.
 *
 * Mirroring the [com.fluxion.inbound.spi.InboundRouter] pattern on the inbound side,
 * this class provides a unified entry point for all outbound transports (HTTP/gRPC/Dubbo/MQ).
 * It resolves the correct [OutboundTransport] by protocol and delegates invocation,
 * providing both blocking [execute] and coroutine-native [executeSuspend] methods.
 *
 * All external function calls from workflow nodes flow through this executor, ensuring
 * consistent error handling, logging, and observability across protocols.
 *
 * @param transportRegistry source of registered outbound transports
 */
package com.fluxion.outbound

import com.fluxion.core.exception.WorkflowNodeException
import kotlinx.coroutines.runBlocking
import org.slf4j.*

class OutboundExecutor(
    private val transportRegistry: OutboundTransportRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Blocking bridge for non-coroutine adapters.
     * Bridges the call onto the coroutine dispatcher via [runBlocking].
     *
     * @param request outbound request carrying config and input payload
     * @return response envelope describing success or structured failure
     */
    fun execute(request: OutboundRequest): OutboundResponse = runBlocking {
        executeSuspend(request)
    }

    /**
     * Primary coroutine-native execution path. Suspends instead of blocking
     * the calling thread, which is essential for WebFlux / virtual-thread pools.
     *
     * Resolution order:
     * 1. Extract protocol from [OutboundConfig.protocol]
     * 2. Resolve transport via [OutboundTransportRegistry.resolve]
     * 3. Delegate to [OutboundTransport.invokeSuspend]
     *
     * @param request outbound request carrying config and input payload
     * @return response envelope describing success or structured failure
     * @throws WorkflowNodeException if no transport is registered for the protocol
     */
    suspend fun executeSuspend(request: OutboundRequest): OutboundResponse {
        val config = request.config
        val transport = transportRegistry.resolve(config.protocol)
            ?: throw WorkflowNodeException(
                request.functionRef,
                UnsupportedOperationException(
                    "No OutboundTransport registered for protocol [${config.protocol}]. " +
                        "Registered protocols: ${transportRegistry.protocols()}"
                )
            )

        log.debug { "Outbound executing [${request.functionRef}] via protocol [${config.protocol}]" }

        return transport.invokeSuspend(request)
    }
}