package com.fluxion.discovery.spring.boot

import com.fluxion.discovery.core.InstanceDiscovery
import com.fluxion.discovery.spring.cloud.SpringCloudInstanceDiscovery
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.cloud.client.discovery.DiscoveryClient"])
class DiscoveryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun instanceDiscovery(discoveryClient: DiscoveryClient): InstanceDiscovery =
        SpringCloudInstanceDiscovery(discoveryClient)
}