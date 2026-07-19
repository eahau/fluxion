package com.fluxion.registry.spring.boot

import com.fluxion.registry.core.InstanceRegistry
import com.fluxion.registry.spring.cloud.SpringCloudInstanceRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.serviceregistry.Registration
import org.springframework.cloud.client.serviceregistry.ServiceRegistry
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.cloud.client.serviceregistry.ServiceRegistry"])
class RegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ServiceRegistry::class)
    fun instanceRegistry(
        serviceRegistry: ServiceRegistry<Registration>,
        discoveryClient: DiscoveryClient
    ): InstanceRegistry = SpringCloudInstanceRegistry(serviceRegistry, discoveryClient)
}