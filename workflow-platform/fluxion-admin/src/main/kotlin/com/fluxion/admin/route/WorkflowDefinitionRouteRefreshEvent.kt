package com.fluxion.admin.route

import org.springframework.context.ApplicationEvent

/**
 * Admin 自举 META 路由刷新事件。
 *
 * 当 wf_definition 中 category = META 的工作流发生发布/下线/删除时，
 * 由 WfDefinitionService 发布此事件，[AdminRouteConfigStore] 监听到后重新加载路由。
 */
class WorkflowDefinitionRouteRefreshEvent(
    source: Any,
    val workflowId: String
) : ApplicationEvent(source)
