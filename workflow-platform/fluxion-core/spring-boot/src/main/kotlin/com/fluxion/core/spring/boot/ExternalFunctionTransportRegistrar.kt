package com.fluxion.core.spring.boot

import com.fluxion.core.function.external.ExternalFunctionTransport
import com.fluxion.core.function.external.ExternalFunctionTransportRegistry
import org.slf4j.*

/**
 * Registers every Spring-discovered [ExternalFunctionTransport] bean
 * with the [ExternalFunctionTransportRegistry] during context init.
 *
 * Keeps the transport layer decoupled from Spring: the registry itself
 * is framework-agnostic (it has a parallel Java ServiceLoader path) and
 * this class only bridges the two worlds.  Also invoked by
 * `BuiltinFunctionRegistrar` so the built-in HTTP/gRPC adapters
 * register into the same registry even when not explicitly declared
 * as beans.
 */
class ExternalFunctionTransportRegistrar(
    registry: ExternalFunctionTransportRegistry,
    transports: List<ExternalFunctionTransport>
) {

    init {
        var count = 0
        transports.forEach { transport ->
            registry.register(transport)
            count++
        }
        log.info { "Registered $count external function transports: ${registry.protocols()}" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ExternalFunctionTransportRegistrar::class.java)
    }
}
