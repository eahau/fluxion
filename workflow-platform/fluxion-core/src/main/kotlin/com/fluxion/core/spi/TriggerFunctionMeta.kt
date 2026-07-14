package com.fluxion.core.spi

import com.fluxion.core.model.TriggerType

/**
 * Mapping result returned by [TriggerFunctionMeta.extractLegacyBinding] so the Admin
 * console can mirror the user-supplied trigger configuration onto the legacy
 * `wf_definition.protocol / method / bind_key` columns and corresponding coarse
 * [TriggerType]. Any field being `null` means "do not overwrite legacy column".
 */
data class LegacyBinding(
    val protocol: String,
    val method: String? = null,
    val bindKey: String? = null,
    val triggerType: TriggerType = TriggerType.API
)

/**
 * Declaration of a single typed parameter that the front-end will render into a
 * dynamic form for a given [TriggerFunctionMeta]. Mirrors the layout used by
 * `NodeParamForm` so the design-tool reuses the same rendering / validation logic
 * for both workflow-node parameters and trigger configuration.
 *
 * This is intentionally a pure data class with zero runtime behaviour so the SPI
 * can be depended upon by admin, all inbound adapters, and worker-runtime without
 * dragging in framework transitive dependencies.
 *
 * @property name          Stable snake_case identifier; used as the JSON key in
 *                         [com.fluxion.core.model.WorkflowTrigger.config] at runtime.
 * @property type          "string" / "number" / "integer" / "boolean" / "enum" /
 *                         "duration_ms".  Unknown types fall back to a free-text input
 *                         on the frontend — no hard-fail.
 * @property required      True if the field must be non-blank / non-null before the
 *                         user can save the workflow.
 * @property label         Short human-readable label shown next to the input.
 * @property description   Optional tooltip / help-text paragraph.
 * @property defaultValue  What to pre-fill when a new trigger of this type is created.
 * @property options       Enum dropdown entries; first element is the value stored,
 *                         second is the translated label.
 */
data class TriggerFunctionParam(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val label: String = name,
    val description: String? = null,
    val defaultValue: Any? = null,
    val options: List<Pair<String, String>>? = null
)

/**
 * SPI contract implemented **once per inbound protocol** (HTTP / Kafka / Dubbo / gRPC / …)
 * and picked up by the Admin console so the workflow designer can render a fully
 * dynamic trigger-configuration drawer without per-protocol hard-coded forms.
 *
 * Each inbound adapter contributes exactly one bean implementing this interface;
 * the Admin `TriggerFunctionMetaRegistry` collects all beans and exposes them via
 * the `GET /api/admin/trigger-functions` endpoint consumed by the designer tool.
 *
 * Runtime behaviour (Kafka listener thread, Spring MVC route registration, Dubbo
 * service export, etc.) is **not** part of this SPI — it lives inside the
 * corresponding inbound adapter and reads parameters from the
 * [com.fluxion.core.model.WorkflowTrigger.config] map.
 */
interface TriggerFunctionMeta {

    val functionRef: String

    val label: String

    val icon: String get() = "ApiOutlined"

    val description: String get() = ""

    val paramSchema: List<TriggerFunctionParam>

    val legacyProtocol: String? get() = functionRef
        .removePrefix("trigger:")
        .substringBeforeLast("Inbound")
        .substringBeforeLast("Consumer")
        .uppercase()
        .takeIf { it in setOf("HTTP", "HTTPS", "KAFKA", "DUBBO", "GRPC") }

    fun extractLegacyBinding(config: Map<String, Any?>): LegacyBinding? = null

    fun configFromLegacy(protocol: String, method: String?, bindKey: String?): Map<String, Any?> = emptyMap()
}
