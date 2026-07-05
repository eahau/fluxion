package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 工作流定义实体。
 *
 * 源表：`wf_definition`
 */
@Entity
@Table(name = "wf_definition")
class WfDefinition : BaseEntity() {

    /** 工作流业务唯一标识（列：`workflow_id`） */
    @Column(name = "workflow_id", nullable = false, unique = true, length = 64)
    var workflowId: String = ""

    /** 工作流显示名称（列：`workflow_name`） */
    @Column(name = "workflow_name", nullable = false, length = 128)
    var workflowName: String = ""

    /** DAG 节点定义（JSON 格式）（列：`dag_json`） */
    @Column(name = "dag_json", nullable = false, columnDefinition = "TEXT")
    var dagJson: String = ""

    /** 入参 JSON Schema（可选）（列：`input_schema`） */
    @Column(name = "input_schema", columnDefinition = "TEXT")
    var inputSchema: String? = null

    /** 入参 Schema 格式标识（列：`input_schema_format`） */
    @Column(name = "input_schema_format", nullable = false, length = 32)
    var inputSchemaFormat: String = "json-schema"

    /** 出参 JSON Schema（可选）（列：`output_schema`） */
    @Column(name = "output_schema", columnDefinition = "TEXT")
    var outputSchema: String? = null

    /** 出参 Schema 格式标识（列：`output_schema_format`） */
    @Column(name = "output_schema_format", nullable = false, length = 32)
    var outputSchemaFormat: String = "json-schema"

    /** 错误处理函数引用（可选）（列：`error_handler_ref`） */
    @Column(name = "error_handler_ref", length = 128)
    var errorHandlerRef: String? = null

    /** 工作流分类：BUSINESS/META（列：`category`） */
    @Column(name = "category", nullable = false, length = 32)
    var category: String = "BUSINESS"

    /** 作用域：PLATFORM/PRIVATE/MARKETPLACE（列：`scope`） */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PRIVATE"

    /** 所属应用分组（scope=PRIVATE 时必填）（列：`app_group`） */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null

    /** 来源引用（安装市场工作流时指向原始 workflowId）（列：`source_ref`） */
    @Column(name = "source_ref", length = 128)
    var sourceRef: String? = null

    /** 协议：HTTP/DUBBO/GRPC/KAFKA/ALL（列：`protocol`） */
    @Column(name = "protocol", nullable = false, length = 32)
    var protocol: String = "HTTP"

    /** 协议方法（列：`method`） */
    @Column(name = "method", length = 20)
    var method: String? = null

    /** 协议绑定键（列：`bind_key`） */
    @Column(name = "bind_key", length = 256)
    var bindKey: String? = null

    /** 发布目标应用群组（JSON 数组）（列：`target_groups`） */
    @Column(name = "target_groups", length = 1024)
    var targetGroups: String? = null

    /** 发布目标（JSON 对象）（列：`publish_target`） */
    @Column(name = "publish_target", length = 1024)
    var publishTarget: String? = null

    /** 触发器配置（JSON 数组）（列：`triggers_config`） */
    @Column(name = "triggers_config", columnDefinition = "TEXT")
    var triggersConfig: String? = null

    /** 版本号（列：`version`） */
    @Column(name = "version", nullable = false)
    var version: Int = 1

    /** 是否受保护，禁止删除（列：`is_protected`） */
    @get:JvmName("getIsProtected")
    @set:JvmName("setIsProtected")
    @Column(name = "is_protected", nullable = false)
    var isProtected: Boolean = false

    /** 状态：DRAFT/ACTIVE/DEPRECATED（列：`status`） */
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "DRAFT"

    /** 事务模式：NONE/SAGA（列：`transaction_mode`） */
    @Column(name = "transaction_mode", length = 16)
    var transactionMode: String = "NONE"

    /** 工作流级装饰器列表（JSON 字符串数组）（列：`workflow_decorators`） */
    @Column(name = "workflow_decorators", columnDefinition = "TEXT")
    var workflowDecorators: String? = null

    /** 工作流级装饰器参数（JSON）（列：`workflow_decorator_params`） */
    @Column(name = "workflow_decorator_params", columnDefinition = "JSON")
    var workflowDecoratorParams: String? = null

    /** 创建人（列：`created_by`） */
    @Column(name = "created_by", length = 64)
    var createdBy: String? = null
}
