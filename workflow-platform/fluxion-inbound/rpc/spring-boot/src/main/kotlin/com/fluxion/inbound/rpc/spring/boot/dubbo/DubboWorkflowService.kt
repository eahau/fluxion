package com.fluxion.inbound.rpc.spring.boot.dubbo

import com.fluxion.inbound.rpc.dubbo.DubboWorkflowApi
import com.fluxion.inbound.rpc.dubbo.DubboWorkflowServiceImpl
import com.fluxion.inbound.spi.InboundRouter
import org.apache.dubbo.config.annotation.DubboService

/**
 * Spring Boot exposed Dubbo service — uses `@DubboService` annotation so the
 * Dubbo spring-boot starter registers this bean with the Dubbo registry
 * (ZooKeeper / Nacos / Redis) at boot time.
 *
 * Implementation is pure delegation to the framework-agnostic
 * [DubboWorkflowServiceImpl] so the transport business logic stays unit-testable
 * without booting Spring / Dubbo.
 *
 * Version + group pins the service at `workflow/1.0.0` which allows multiple
 * workflow-engine generations to co-exist in the same registry during a
 * rolling upgrade without traffic bleed.
 */
@DubboService(version = "1.0.0", group = "workflow")
open class DubboWorkflowService(
    inboundRouter: InboundRouter
) : DubboWorkflowApi by DubboWorkflowServiceImpl(inboundRouter)
