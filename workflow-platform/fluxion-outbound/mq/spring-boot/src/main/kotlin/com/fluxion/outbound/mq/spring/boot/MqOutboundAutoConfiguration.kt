package com.fluxion.outbound.mq.spring.boot

import com.fluxion.outbound.mq.spi.MqPublisher
import com.fluxion.outbound.mq.MqOutboundTransport
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(MqPublisher::class)
class MqOutboundAutoConfiguration {

    @Bean
    @ConditionalOnBean(MqPublisher::class)
    @ConditionalOnMissingBean(MqOutboundTransport::class)
    fun mqOutboundTransport(
        mqPublisher: MqPublisher
    ): MqOutboundTransport = MqOutboundTransport(mqPublisher)
}
