-- ============================================================
--  Admin 自举元工作流（META）初始化脚本
--  职责：将函数/工作流定义的发布、下线等管理操作下沉到工作流引擎执行
--  加载条件：category = META, status = ACTIVE, protocol = HTTP, bind_key 非空
--
--  前置约束：
--    1. wf_definition 表必须存在 workflow_id 业务唯一键，否则本脚本的
--       幂等语义无法生效，会导致重复插入。
--       若缺失该约束，请先执行：
--         ALTER TABLE wf_definition ADD UNIQUE KEY uk_workflow_id (workflow_id);
--    2. 若 wf_definition 表尚未创建，请先执行：
--         fluxion-admin/src/main/resources/db/migration/V1__init_workflow_platform.sql
--       中的 CREATE TABLE 语句。
--
--  命名约定（与现有 admin-function-publish / admin-function-deprecate 保持一致）：
--    workflow_id: admin-{resource}-{action}
--    bind_key:    /api/admin/{resources}/{resourceId}/{action}
-- ============================================================

-- ── 1. 函数发布 ──────────────────────────────────────────────
-- 路由：POST /api/admin/functions/{functionName}/publish
INSERT INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`, `input_schema`, `output_schema`,
    `protocol`, `method`, `bind_key`, `category`, `version`, `status`,
    `transaction_mode`, `is_protected`, `scope`, `created_at`, `updated_at`
) VALUES (
    'admin-function-publish',
    'Admin Function Publish',
    '{"nodes":[{"id":"publish","name":"publish","type":"CUSTOM","functionRef":"admin:publishFunction","timeoutMs":30000,"params":{},"dependsOn":[]}]}',
    '{"type":"object","properties":{"functionName":{"type":"string"},"publishTarget":{"type":"object"}},"required":["functionName"]}',
    '{"type":"object"}',
    'HTTP', 'POST', '/api/admin/functions/{functionName}/publish',
    'META', 1, 'ACTIVE', 'NONE', 1, 'PLATFORM',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    `workflow_name`    = VALUES(`workflow_name`),
    `dag_json`         = VALUES(`dag_json`),
    `input_schema`     = VALUES(`input_schema`),
    `output_schema`    = VALUES(`output_schema`),
    `protocol`         = VALUES(`protocol`),
    `method`           = VALUES(`method`),
    `bind_key`         = VALUES(`bind_key`),
    `category`         = VALUES(`category`),
    `version`          = VALUES(`version`),
    `status`           = VALUES(`status`),
    `transaction_mode` = VALUES(`transaction_mode`),
    `is_protected`     = VALUES(`is_protected`),
    `scope`            = VALUES(`scope`),
    `updated_at`       = VALUES(`updated_at`);

-- ── 2. 函数下线 ──────────────────────────────────────────────
-- 路由：POST /api/admin/functions/{functionName}/deprecate
INSERT INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`, `input_schema`, `output_schema`,
    `protocol`, `method`, `bind_key`, `category`, `version`, `status`,
    `transaction_mode`, `is_protected`, `scope`, `created_at`, `updated_at`
) VALUES (
    'admin-function-deprecate',
    'Admin Function Deprecate',
    '{"nodes":[{"id":"deprecate","name":"deprecate","type":"CUSTOM","functionRef":"admin:deprecateFunction","timeoutMs":30000,"params":{},"dependsOn":[]}]}',
    '{"type":"object","properties":{"functionName":{"type":"string"}},"required":["functionName"]}',
    '{"type":"object"}',
    'HTTP', 'POST', '/api/admin/functions/{functionName}/deprecate',
    'META', 1, 'ACTIVE', 'NONE', 1, 'PLATFORM',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    `workflow_name`    = VALUES(`workflow_name`),
    `dag_json`         = VALUES(`dag_json`),
    `input_schema`     = VALUES(`input_schema`),
    `output_schema`    = VALUES(`output_schema`),
    `protocol`         = VALUES(`protocol`),
    `method`           = VALUES(`method`),
    `bind_key`         = VALUES(`bind_key`),
    `category`         = VALUES(`category`),
    `version`          = VALUES(`version`),
    `status`           = VALUES(`status`),
    `transaction_mode` = VALUES(`transaction_mode`),
    `is_protected`     = VALUES(`is_protected`),
    `scope`            = VALUES(`scope`),
    `updated_at`       = VALUES(`updated_at`);

-- ── 3. 工作流定义发布 ─────────────────────────────────────────
-- 路由：POST /api/admin/workflows/{workflowId}/publish
INSERT INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`, `input_schema`, `output_schema`,
    `protocol`, `method`, `bind_key`, `category`, `version`, `status`,
    `transaction_mode`, `is_protected`, `scope`, `created_at`, `updated_at`
) VALUES (
    'admin-workflow-publish',
    'Admin Workflow Publish',
    '{"nodes":[{"id":"publish","name":"publish","type":"CUSTOM","functionRef":"admin:publishWorkflow","timeoutMs":30000,"params":{},"dependsOn":[]}]}',
    '{"type":"object","properties":{"workflowId":{"type":"string"},"publishTarget":{"type":"object"}},"required":["workflowId"]}',
    '{"type":"object"}',
    'HTTP', 'POST', '/api/admin/workflows/{workflowId}/publish',
    'META', 1, 'ACTIVE', 'NONE', 1, 'PLATFORM',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    `workflow_name`    = VALUES(`workflow_name`),
    `dag_json`         = VALUES(`dag_json`),
    `input_schema`     = VALUES(`input_schema`),
    `output_schema`    = VALUES(`output_schema`),
    `protocol`         = VALUES(`protocol`),
    `method`           = VALUES(`method`),
    `bind_key`         = VALUES(`bind_key`),
    `category`         = VALUES(`category`),
    `version`          = VALUES(`version`),
    `status`           = VALUES(`status`),
    `transaction_mode` = VALUES(`transaction_mode`),
    `is_protected`     = VALUES(`is_protected`),
    `scope`            = VALUES(`scope`),
    `updated_at`       = VALUES(`updated_at`);

-- ── 4. 工作流定义下线 ─────────────────────────────────────────
-- 路由：POST /api/admin/workflows/{workflowId}/deprecate
INSERT INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`, `input_schema`, `output_schema`,
    `protocol`, `method`, `bind_key`, `category`, `version`, `status`,
    `transaction_mode`, `is_protected`, `scope`, `created_at`, `updated_at`
) VALUES (
    'admin-workflow-deprecate',
    'Admin Workflow Deprecate',
    '{"nodes":[{"id":"deprecate","name":"deprecate","type":"CUSTOM","functionRef":"admin:deprecateWorkflow","timeoutMs":30000,"params":{},"dependsOn":[]}]}',
    '{"type":"object","properties":{"workflowId":{"type":"string"}},"required":["workflowId"]}',
    '{"type":"object"}',
    'HTTP', 'POST', '/api/admin/workflows/{workflowId}/deprecate',
    'META', 1, 'ACTIVE', 'NONE', 1, 'PLATFORM',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON DUPLICATE KEY UPDATE
    `workflow_name`    = VALUES(`workflow_name`),
    `dag_json`         = VALUES(`dag_json`),
    `input_schema`     = VALUES(`input_schema`),
    `output_schema`    = VALUES(`output_schema`),
    `protocol`         = VALUES(`protocol`),
    `method`           = VALUES(`method`),
    `bind_key`         = VALUES(`bind_key`),
    `category`         = VALUES(`category`),
    `version`          = VALUES(`version`),
    `status`           = VALUES(`status`),
    `transaction_mode` = VALUES(`transaction_mode`),
    `is_protected`     = VALUES(`is_protected`),
    `scope`            = VALUES(`scope`),
    `updated_at`       = VALUES(`updated_at`);
