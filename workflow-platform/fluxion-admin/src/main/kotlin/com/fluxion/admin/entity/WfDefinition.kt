package com.fluxion.admin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Workflow definition entity — the canonical store for a published DAG.
 *
 * Captures the full workflow lifecycle: DAG structure (`dagJson`), input/output schemas,
 * protocol bindings (HTTP/DUBBO/GRPC/KAFKA), transaction mode, decorators, and status
 * (DRAFT / ACTIVE / DEPRECATED). WorkflowEngine reads the DAG at execution time; Admin
 * controllers provide CRUD + publish/deprecate operations.
 *
 * Source table: `wf_definition`.
 *
 * Collaborates with: WfDefinitionRepository, WfDefinitionService, WorkflowEngine (runtime
 * executor), AdminRouteConfigStore (protocol binding refresh), SchemaCompatibilityValidator
 * (input/output schema evolution checks).
 */
@Entity
@Table(name = "wf_definition")
class WfDefinition : BaseEntity() {

    /** Business-unique workflow identifier, maps to column `workflow_id`. */
    @Column(name = "workflow_id", nullable = false, unique = true, length = 64)
    var workflowId: String = ""

    /** Human-readable display name, maps to column `workflow_name`. */
    @Column(name = "workflow_name", nullable = false, length = 128)
    var workflowName: String = ""

    /** DAG node+edge definition JSON, maps to column `dag_json`. */
    @Column(name = "dag_json", nullable = false, columnDefinition = "TEXT")
    var dagJson: String = ""

    /** Input-parameter JSON Schema reference (optional), maps to column `input_schema`. */
    @Column(name = "input_schema", columnDefinition = "TEXT")
    var inputSchema: String? = null

    /** Input schema format identifier, maps to column `input_schema_format`. */
    @Column(name = "input_schema_format", nullable = false, length = 32)
    var inputSchemaFormat: String = "json-schema"

    /** Output-parameter JSON Schema reference (optional), maps to column `output_schema`. */
    @Column(name = "output_schema", columnDefinition = "TEXT")
    var outputSchema: String? = null

    /** Output schema format identifier, maps to column `output_schema_format`. */
    @Column(name = "output_schema_format", nullable = false, length = 32)
    var outputSchemaFormat: String = "json-schema"

    /** Optional compensation/error-handler function reference, maps to column `error_handler_ref`. */
    @Column(name = "error_handler_ref", length = 128)
    var errorHandlerRef: String? = null

    /** Classification: BUSINESS (user workflow) / META (admin/orchestration), maps to column `category`. */
    @Column(name = "category", nullable = false, length = 32)
    var category: String = "BUSINESS"

    /** Visibility: PLATFORM / PRIVATE / MARKETPLACE, maps to column `scope`. */
    @Column(name = "scope", nullable = false, length = 16)
    var scope: String = "PRIVATE"

    /** Owning app group when scope=PRIVATE, maps to column `app_group`. */
    @Column(name = "app_group", length = 128)
    var appGroup: String? = null

    /** Original workflowId when installed from Marketplace, maps to column `source_ref`. */
    @Column(name = "source_ref", length = 128)
    var sourceRef: String? = null

    /** Binding protocol: HTTP / DUBBO / GRPC / KAFKA / ALL, maps to column `protocol`. */
    @Column(name = "protocol", nullable = false, length = 32)
    var protocol: String = "HTTP"

    /** Protocol method (GET/POST for HTTP, etc.), maps to column `method`. */
    @Column(name = "method", length = 20)
    var method: String? = null

    /** Protocol binding key (path/topic/service-name), maps to column `bind_key`. */
    @Column(name = "bind_key", length = 256)
    var bindKey: String? = null

    /** JSON array of target deployment app groups, maps to column `target_groups`. */
    @Column(name = "target_groups", length = 1024)
    var targetGroups: String? = null

    /** JSON object describing publish target details, maps to column `publish_target`. */
    @Column(name = "publish_target", length = 1024)
    var publishTarget: String? = null

    /** Trigger configuration JSON array (cron/http/mq), maps to column `triggers_config`. */
    @Column(name = "triggers_config", columnDefinition = "TEXT")
    var triggersConfig: String? = null

    /** Monotonic version incremented on each publish, maps to column `version`. */
    @Column(name = "version", nullable = false)
    var version: Int = 1

    /** Protected workflows cannot be deleted via UI/API, maps to column `is_protected`. */
    @get:JvmName("getIsProtected")
    @set:JvmName("setIsProtected")
    @Column(name = "is_protected", nullable = false)
    var isProtected: Boolean = false

    /** Lifecycle state: DRAFT / ACTIVE / DEPRECATED, maps to column `status`. */
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "DRAFT"

    /** Transaction semantics: NONE (default) / SAGA, maps to column `transaction_mode`. */
    @Column(name = "transaction_mode", length = 16)
    var transactionMode: String = "NONE"

    /** JSON string array of workflow-level decorator names, maps to column `workflow_decorators`. */
    @Column(name = "workflow_decorators", columnDefinition = "TEXT")
    var workflowDecorators: String? = null

    /** JSON object of workflow-level decorator parameters, maps to column `workflow_decorator_params`. */
    @Column(name = "workflow_decorator_params", columnDefinition = "JSON")
    var workflowDecoratorParams: String? = null

    /** Username who created this definition, maps to column `created_by`. */
    @Column(name = "created_by", length = 64)
    var createdBy: String? = null
}
