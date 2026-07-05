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
 * Jackson 容错反序列化模块
 *
 * 解决 AI 接入场景下的核心脆弱点：
 * OpenAPI 生成的枚举 forValue() 对未知值直接抛 NoSuchElementException，
 * 导致 Jackson 反序列化失败。本模块为所有可能接收外部输入的生成枚举注册
 * 兜底反序列化器，确保未知值降级为合理默认值而非崩溃。
 *
 * 设计原则：AI 生成的数据不可信，在反序列化层即做容错，而非在业务层补救。
 */
@Component
class JacksonSafeEnumModule : SimpleModule("JacksonSafeEnumModule") {

    init {
        // NodeType 是最关键的枚举 — dag_json 中的 node type 字段
        // AI 可能生成 BUILTIN/EXTERNAL 等核心类型或任意未知值
        addDeserializer(NodeType::class.java, SafeNodeTypeDeserializer())

        // ErrorStrategy: AI 可能生成未知的错误策略
        addDeserializer(ErrorStrategy::class.java, SafeErrorStrategyDeserializer())

        // WorkflowCategory: AI 可能生成未知分类
        addDeserializer(WorkflowCategory::class.java, SafeWorkflowCategoryDeserializer())

        // WorkflowStatus: AI 可能生成未知状态
        addDeserializer(WorkflowStatus::class.java, SafeWorkflowStatusDeserializer())

        // FunctionNodeType: 函数定义中的节点类型
        addDeserializer(FunctionNodeType::class.java, SafeFunctionNodeTypeDeserializer())
    }

    /**
     * NodeType 安全反序列化：未知值降级为 CUSTOM
     */
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
