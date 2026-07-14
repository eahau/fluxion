package com.fluxion.admin.route

import org.springframework.context.ApplicationEvent

/**
 * Spring application event fired when a META-category workflow definition transitions into
 * or out of the active state (publish / deprecate / delete).
 *
 * Published by `WfDefinitionService` and consumed by `AdminRouteConfigStore`, which reloads
 * the dynamic HTTP route table so the admin console's self-hosted meta-endpoints stay in
 * sync with the database state.
 */
class WorkflowDefinitionRouteRefreshEvent(
    source: Any,
    val workflowId: String
) : ApplicationEvent(source)
