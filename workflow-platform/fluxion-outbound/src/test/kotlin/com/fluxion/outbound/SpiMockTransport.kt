package com.fluxion.outbound

/** Mock transport loaded via ServiceLoader for SPI testing. */
class SpiMockTransport : OutboundTransport {
    override fun protocol(): String = "spi-mock"
    override fun invoke(request: OutboundRequest): OutboundResponse =
        OutboundResponse(output = "spi-loaded")
}