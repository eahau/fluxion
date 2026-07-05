package com.fluxion.adapter.rpc.spring.boot.dubbo

import com.fluxion.adapter.dubbo.DubboWorkflowApi
import com.fluxion.adapter.dubbo.DubboWorkflowServiceImpl
import com.fluxion.adapter.spi.WorkflowRouter
import org.apache.dubbo.config.annotation.DubboService

/**
 * Dubbo 工作流服务 Spring Boot 暴露
 *
 * 零 Spring 实现参见 workflow-adapter-rpc-dubbo 的 DubboWorkflowServiceImpl
 */
@DubboService(version = "1.0.0", group = "workflow")
open class DubboWorkflowService(
    workflowRouter: WorkflowRouter
) : DubboWorkflowApi by DubboWorkflowServiceImpl(workflowRouter)
