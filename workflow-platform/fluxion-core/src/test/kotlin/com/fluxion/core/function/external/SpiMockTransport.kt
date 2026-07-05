package com.fluxion.core.function.external

/**
 * 仅用于 ServiceLoader 测试的 mock transport。
 */
class SpiMockTransport : ExternalFunctionTransport {
    override fun protocol(): String = "spi-mock"
    override fun invoke(request: ExternalFunctionRequest): ExternalFunctionResponse =
        ExternalFunctionResponse(output = "spi-loaded")
}
