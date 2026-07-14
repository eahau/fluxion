package com.fluxion.inbound.rpc.spring.boot.dubbo

import com.fluxion.core.spi.TriggerFunctionMeta
import com.fluxion.inbound.rpc.dubbo.DubboInboundTriggerMeta
import com.fluxion.inbound.spi.InboundRouter
import org.slf4j.LoggerFactory
import org.slf4j.info
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(
    name = [
        "org.apache.dubbo.config.annotation.DubboService",
        "com.fluxion.inbound.rpc.dubbo.DubboWorkflowApi"
    ]
)
@ConditionalOnProperty(name = ["workflow.rpc.dubbo.enabled"], havingValue = "true", matchIfMissing = true)
class DubboRpcAdapterAutoConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun dubboInboundTriggerMeta(): TriggerFunctionMeta = DubboInboundTriggerMeta()

    @Bean
    @ConditionalOnMissingBean
    fun dubboWorkflowService(
        inboundRouter: InboundRouter
    ): DubboWorkflowService {
        log.info { "Registering DubboWorkflowService (version=1.0.0, group=workflow)" }
        return DubboWorkflowService(inboundRouter)
    }
}
