package com.fluxion.core.function.external

/**
 * 娴犲懐鏁ゆ禍?ServiceLoader 濞村鐦惃?mock transport閵? */
class SpiMockTransport : ExternalFunctionTransport {
    override fun protocol(): String = "spi-mock"
    override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse =
        ExternalFunctionResponse(output = "spi-loaded")
}
