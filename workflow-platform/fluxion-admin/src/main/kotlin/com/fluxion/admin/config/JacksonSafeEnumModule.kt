package com.fluxion.admin.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fluxion.admin.generated.model.ErrorStrategy
import com.fluxion.admin.generated.model.FunctionNodeType
import com.fluxion.admin.generated.model.NodeType
import com.fluxion.admin.generated.model.WorkflowCategory
import com.fluxion.admin.generated.model.WorkflowStatus
import org.springframework.stereotype.Component

/**
 * Jackson module that registers lenient deserializers for every OpenAPI-generated enum
 * that might receive external (AI-authored, user-uploaded) input.
 *
 * The default OpenAPI enum `forValue()` throws `NoSuchElementException` on unknown values,
 * which bubbles up and crashes the whole deserialization. Each deserializer in this module
 * instead gracefully falls back to a sensible default, aligning with the design rule:
 * **AI-generated data is untrusted and must be sanitized at the deserialization boundary,
 * not patched up later by business logic.**
 */
@Component
class JacksonSafeEnumModule : SimpleModule("JacksonSafeEnumModule") {

    init {
        addDeserializer(NodeType::class.java, SafeNodeTypeDeserializer())
        addDeserializer(ErrorStrategy::class.java, SafeErrorStrategyDeserializer())
        addDeserializer(WorkflowCategory::class.java, SafeWorkflowCategoryDeserializer())
        addDeserializer(WorkflowStatus::class.java, SafeWorkflowStatusDeserializer())
        addDeserializer(FunctionNodeType::class.java, SafeFunctionNodeTypeDeserializer())
    }

    /** NodeType: falls back to `CUSTOM` when encountering BUILTIN/EXTERNAL/unknown values in a `dag_json`. */
    class SafeNodeTypeDeserializer : JsonDeserializer<NodeType>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): NodeType {
            val value = p.text ?: return NodeType.CUSTOM
            return NodeType.entries.firstOrNull { it.value == value } ?: NodeType.CUSTOM
        }
    }

    class SafeErrorStrategyDeserializer : JsonDeserializer<ErrorStrategy>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ErrorStrategy {
            val value = p.text ?: return ErrorStrategy.FAIL
            return ErrorStrategy.entries.firstOrNull { it.value == value } ?: ErrorStrategy.FAIL
        }
    }

    class SafeWorkflowCategoryDeserializer : JsonDeserializer<WorkflowCategory>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WorkflowCategory {
            val value = p.text ?: return WorkflowCategory.BUSINESS
            return WorkflowCategory.entries.firstOrNull { it.value == value } ?: WorkflowCategory.BUSINESS
        }
    }

    class SafeWorkflowStatusDeserializer : JsonDeserializer<WorkflowStatus>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WorkflowStatus {
            val value = p.text ?: return WorkflowStatus.DRAFT
            return WorkflowStatus.entries.firstOrNull { it.value == value } ?: WorkflowStatus.DRAFT
        }
    }

    class SafeFunctionNodeTypeDeserializer : JsonDeserializer<FunctionNodeType>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FunctionNodeType {
            val value = p.text ?: return FunctionNodeType.CUSTOM
            return FunctionNodeType.entries.firstOrNull { it.value == value } ?: FunctionNodeType.CUSTOM
        }
    }
}
