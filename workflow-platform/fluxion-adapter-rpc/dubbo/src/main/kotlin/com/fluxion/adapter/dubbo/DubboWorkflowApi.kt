package com.fluxion.adapter.dubbo

/**
 * Dubbo service contract — exposed by the workflow engine and consumed by
 * remote Dubbo clients.
 *
 * There are **three routing modes** a caller can choose from (highest priority
 * first — once a match is found the lower-priority rules are ignored):
 *
 * 1. **Direct (strongest)**: include `_workflowId` in the params map. The
 *    router will short-circuit and execute that exact workflow. This is the
 *    safest option when the caller already knows the workflow id (e.g. the
 *    caller looked it up via the admin API first).
 *
 * 2. **Preferred (Dubbo metadata)**: pass `x-service-key` as a Dubbo
 *    attachment. Mirrors the convention used by the HTTP adapter
 *    (`X-Service-Key` header) and the gRPC adapter (`x-service-key`
 *    metadata), so client-side tooling (gateways, sdks) can reuse the same
 *    key across transport stacks.
 *
 * 3. **Compatibility (payload)**: include `_serviceKey` in the params map.
 *    Maintained for legacy clients that cannot set Dubbo attachments.
 */
interface DubboWorkflowApi {

    /**
     * Execute a workflow over Dubbo.
     *
     * @param params Business parameters (plus optional routing keys `_workflowId`
     *               and/or `_serviceKey` for modes 1 / 3 above).
     * @return The workflow result map on success, or a map
     *         `{success:false, errorCode, message}` on failure.
     */
    fun execute(params: Map<String, Any>): Any?
}
