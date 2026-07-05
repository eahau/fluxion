-- Flyway Migration: 工作流平台完整 DDL（合并 V1~V8 最终状态）
-- 说明：将原 V1~V8 增量迁移合并为单条初始化 SQL，重建库时直接执行本文件即可。

-- ═══════════════════════════════════════════════════════════════════
-- 1. Spring Security 标准用户/角色/权限表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `users` (
    `username`   VARCHAR(64)  PRIMARY KEY COMMENT '用户名',
    `password`   VARCHAR(256) NOT NULL COMMENT '密码（BCrypt 加密）',
    `enabled`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `nickname`   VARCHAR(64)           COMMENT '昵称',
    `email`      VARCHAR(128)          COMMENT '邮箱',
    `phone`      VARCHAR(32)           COMMENT '手机号',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_users_email` (`email`),
    INDEX `idx_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `roles` (
    `role_name`   VARCHAR(64) PRIMARY KEY COMMENT '角色名称（唯一标识）',
    `description` VARCHAR(256)         COMMENT '角色描述',
    `created_at`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `user_roles` (
    `username`  VARCHAR(64) NOT NULL COMMENT '用户名',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    PRIMARY KEY (`username`, `role_name`),
    FOREIGN KEY (`username`)  REFERENCES `users` (`username`)  ON DELETE CASCADE,
    FOREIGN KEY (`role_name`) REFERENCES `roles` (`role_name`) ON DELETE CASCADE,
    INDEX `idx_user_roles_username` (`username`),
    INDEX `idx_user_roles_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `role_permissions` (
    `role_name`  VARCHAR(64)  NOT NULL COMMENT '角色名称',
    `permission` VARCHAR(100) NOT NULL COMMENT '权限标识',
    PRIMARY KEY (`role_name`, `permission`),
    FOREIGN KEY (`role_name`) REFERENCES `roles` (`role_name`) ON DELETE CASCADE,
    INDEX `idx_role_permissions_role_name` (`role_name`),
    INDEX `idx_role_permissions_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ═══════════════════════════════════════════════════════════════════
-- 2. 用户-应用分组关联（多租户隔离）
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `user_app_groups` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(64)  NOT NULL COMMENT '用户名',
    `app_group`  VARCHAR(128) NOT NULL COMMENT '应用分组',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_app_group` (`username`, `app_group`),
    INDEX `idx_username` (`username`),
    INDEX `idx_app_group` (`app_group`),
    CONSTRAINT `fk_uag_user` FOREIGN KEY (`username`) REFERENCES `users` (`username`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户应用分组关联（多租户隔离）';

-- ═══════════════════════════════════════════════════════════════════
-- 3. wf_definition 工作流定义表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_definition` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT,
    `workflow_id`               VARCHAR(64)  NOT NULL COMMENT '工作流业务唯一标识',
    `workflow_name`             VARCHAR(128) NOT NULL COMMENT '工作流显示名称',
    `dag_json`                  TEXT         NOT NULL COMMENT 'DAG 节点定义（JSON格式）',
    `input_schema`              TEXT                  COMMENT '入参 JSON Schema（可选）',
    `output_schema`             TEXT                  COMMENT '出参 JSON Schema（可选）',
    `error_handler_ref`         VARCHAR(128)          COMMENT '错误处理函数引用（可选）',
    `category`                  VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS' COMMENT '工作流分类：BUSINESS/META',
    `is_protected`              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=受保护元工作流，禁止删除',
    `scope`                     VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PLATFORM/PRIVATE/MARKETPLACE',
    `app_group`                 VARCHAR(128)          COMMENT '所属应用分组（scope=PRIVATE 时必填）',
    `source_ref`                VARCHAR(128)          COMMENT '来源引用（安装市场工作流时指向原始 workflowId）',
    `protocol`                  VARCHAR(32)  NOT NULL DEFAULT 'HTTP' COMMENT '协议：HTTP/DUBBO/GRPC/KAFKA/ALL',
    `method`                    VARCHAR(20)           COMMENT '协议方法（HTTP方法/gRPC调用类型/Dubbo方法名）',
    `bind_key`                  VARCHAR(256)          COMMENT '协议绑定键（HTTP=path, Dubbo=serviceKey, Kafka=topic）',
    `target_groups`             VARCHAR(1024)         COMMENT '发布目标应用群组（JSON数组，NULL=全量广播）',
    `publish_target`            VARCHAR(1024)         COMMENT '发布目标（JSON对象，包含 type/groups/instanceIds）',
    `triggers_config`           TEXT                  COMMENT '触发器配置（JSON数组存储 WorkflowTrigger 列表）',
    `version`                   INT          NOT NULL DEFAULT 1,
    `status`                    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DEPRECATED',
    `transaction_mode`          VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SAGA',
    `workflow_decorators`       TEXT                  COMMENT '工作流级装饰器列表（JSON字符串数组）',
    `workflow_decorator_params` JSON                  COMMENT '工作流级装饰器参数（key=装饰器名，value=参数Map）',
    `created_by`                VARCHAR(64),
    `created_at`                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_id` (`workflow_id`),
    KEY `idx_protocol_bind` (`protocol`, `bind_key`(128)),
    KEY `idx_scope_route` (`scope`, `app_group`, `protocol`, `method`, `bind_key`(128)),
    KEY `idx_status` (`status`),
    CONSTRAINT `chk_workflow_id_not_empty` CHECK (`workflow_id` <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表';

-- ═══════════════════════════════════════════════════════════════════
-- 4. wf_function 函数注册表
-- ═══════════════════════════════════════════════════════════════════
-- 采用混合模式：保留高频检索/约束列，其余配置统一收敛到 config JSON
CREATE TABLE IF NOT EXISTS `wf_function` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `function_name` VARCHAR(128) NOT NULL COMMENT '函数引用名（如 builtin:httpCall）',
    `function_type` VARCHAR(32)  NOT NULL DEFAULT 'BUILTIN' COMMENT 'BUILTIN/SCRIPT_GROOVY/SCRIPT_JS/JAVA_CLASS/EXTERNAL/CUSTOM',
    `scope`         VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PLATFORM/PRIVATE/MARKETPLACE',
    `app_group`     VARCHAR(128)          COMMENT '所属应用分组（scope=PRIVATE 时必填）',
    `source_ref`    VARCHAR(128)          COMMENT '来源引用（安装市场函数时指向原始 functionName）',
    `domain`        VARCHAR(32)           COMMENT '函数所属领域（db/http/redis/cache/common/script）',
    `config`        JSON                  COMMENT '统一配置 JSON（description/paramSchema/outputSchema/scriptBody/className/publishTarget 等）',
    `status`        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope_app_function` (`scope`, `app_group`, `function_name`),
    KEY `idx_type_status` (`function_type`, `status`),
    KEY `idx_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流函数注册表（混合模式）';

-- ═══════════════════════════════════════════════════════════════════
-- 5. wf_schema 数据 Schema 表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_schema` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `schema_name` VARCHAR(128) NOT NULL COMMENT 'Schema 唯一名称',
    `schema_type` VARCHAR(64)  NOT NULL DEFAULT 'INPUT' COMMENT '类型标签（逗号分隔，如 INPUT,OUTPUT），支持多类型复用',
    `schema_json` TEXT         NOT NULL COMMENT 'JSON Schema 内容',
    `description` VARCHAR(512),
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schema_name` (`schema_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据 Schema 表';

-- ═══════════════════════════════════════════════════════════════════
-- 6. wf_resource_template 资源模板表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_resource_template` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `resource_key`  VARCHAR(128) NOT NULL COMMENT '资源唯一键',
    `resource_type` VARCHAR(32)  NOT NULL COMMENT 'REDIS_KEY/MQ_TOPIC/HTTP_ENDPOINT/DB_TABLE',
    `template`      TEXT         NOT NULL COMMENT '资源模板（Key模板/URL/表名等，支持 SpEL 占位符）',
    `description`   VARCHAR(512),
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resource_key` (`resource_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源模板表（通用）';

-- ═══════════════════════════════════════════════════════════════════
-- 7. wf_execution_log 执行日志表（可选持久化）
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_execution_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `execution_id`  VARCHAR(64)  NOT NULL COMMENT '工作流执行唯一 ID',
    `workflow_id`   VARCHAR(64)  NOT NULL,
    `node_id`       VARCHAR(64)  NOT NULL COMMENT 'null=工作流级汇总',
    `node_name`     VARCHAR(128),
    `status`        VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED/SKIPPED/FALLBACK',
    `input_json`    TEXT         COMMENT '节点输入（调试用）',
    `output_json`   TEXT         COMMENT '节点输出',
    `error_message` TEXT         COMMENT '错误信息',
    `duration_ms`   BIGINT       COMMENT '执行耗时（毫秒）',
    `retry_attempt` INT          DEFAULT 0,
    `trace_id`      VARCHAR(64)  COMMENT '链路追踪 ID',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_workflow_created` (`workflow_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点执行日志（可选持久化）';

-- ═══════════════════════════════════════════════════════════════════
-- 8. wf_execution_snapshot 执行快照表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_execution_snapshot` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `execution_id`      VARCHAR(64)   NOT NULL COMMENT '执行唯一 ID（来自 EngineResult.executionId）',
    `workflow_id`       VARCHAR(64)   NOT NULL COMMENT '工作流 ID',
    `workflow_version`  INT           NOT NULL DEFAULT 1 COMMENT '执行时的工作流版本',
    `success`           TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '执行是否成功',
    `inputs_json`       MEDIUMTEXT    COMMENT '工作流原始入参（Map<String,Object> JSON）',
    `node_outputs_json` MEDIUMTEXT    COMMENT '各节点输出快照（Map<nodeId,output> JSON）',
    `trace_json`        MEDIUMTEXT    COMMENT '节点执行轨迹（List<NodeExecutionRecord> JSON）',
    `error_msg`         VARCHAR(512)  COMMENT '错误信息（失败时有值）',
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_id` (`execution_id`),
    KEY `idx_workflow_id_created` (`workflow_id`, `created_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行快照（调试/重放使用）';

-- ═══════════════════════════════════════════════════════════════════
-- 9. wf_audit_log 审计日志表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_audit_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `operator`      VARCHAR(64)  NOT NULL COMMENT '操作人账号',
    `action`        VARCHAR(64)  NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/PUBLISH/OFFLINE',
    `resource_type` VARCHAR(64)  NOT NULL COMMENT '资源类型：WORKFLOW/FUNCTION/SCHEMA/USER/ROLE',
    `resource_id`   VARCHAR(128) NOT NULL COMMENT '资源业务 ID',
    `detail`        VARCHAR(1024) COMMENT '操作详情（JSON）',
    `ip`            VARCHAR(64)  COMMENT '操作人 IP',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator`),
    KEY `idx_resource` (`resource_type`, `resource_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ═══════════════════════════════════════════════════════════════════
-- 10. wf_marketplace_listing 市场发布记录表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_marketplace_listing` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_id`      VARCHAR(128) NOT NULL COMMENT '市场 ID（如 user-login-flow）',
    `source_type`     VARCHAR(16)  NOT NULL COMMENT '来源类型: WORKFLOW/FUNCTION/WORKFLOW_TEMPLATE',
    `source_id`       VARCHAR(128) NOT NULL COMMENT '源工作流/函数 ID',
    `source_version`  INT          NOT NULL COMMENT '发布时锁定的版本',
    `title`           VARCHAR(256) NOT NULL,
    `description`     TEXT                  COMMENT '市场描述',
    `tags`            JSON                  COMMENT '标签列表',
    `author`          VARCHAR(64)  NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DEPRECATED',
    `install_count`   INT          NOT NULL DEFAULT 0,
    `template_config` TEXT                  COMMENT '模版配置（仅 WORKFLOW_TEMPLATE 类型使用）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_listing_id` (`listing_id`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场发布记录';

-- ═══════════════════════════════════════════════════════════════════
-- 11. wf_marketplace_install 市场安装记录表
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS `wf_marketplace_install` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_id`    VARCHAR(128) NOT NULL COMMENT '市场 listing ID',
    `app_group`     VARCHAR(128) NOT NULL COMMENT '安装者应用分组',
    `installed_by`  VARCHAR(64)           COMMENT '安装人',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_listing_app` (`listing_id`, `app_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='市场安装记录';

-- ═══════════════════════════════════════════════════════════════════
-- 12. 初始化数据
-- ═══════════════════════════════════════════════════════════════════

-- 初始化角色
INSERT IGNORE INTO `roles` (`role_name`, `description`) VALUES
    ('ADMIN',     '全部权限'),
    ('DEVELOPER', '开发工作流'),
    ('OPERATOR',  '运维操作'),
    ('VIEWER',    '只读');

-- 初始化角色权限
INSERT IGNORE INTO `role_permissions` (`role_name`, `permission`) VALUES
    ('ADMIN',     'user:manage'),
    ('ADMIN',     'role:manage'),
    ('ADMIN',     'workflow:publish'),
    ('ADMIN',     'workflow:delete'),
    ('ADMIN',     'function:manage'),
    ('ADMIN',     'schema:manage'),
    ('DEVELOPER', 'workflow:edit'),
    ('DEVELOPER', 'workflow:publish'),
    ('DEVELOPER', 'function:view'),
    ('DEVELOPER', 'schema:view'),
    ('OPERATOR',  'workflow:publish'),
    ('OPERATOR',  'workflow:deprecate'),
    ('OPERATOR',  'monitor:view'),
    ('VIEWER',    'workflow:view'),
    ('VIEWER',    'function:view'),
    ('VIEWER',    'schema:view'),
    ('VIEWER',    'monitor:view');

-- 初始化默认管理员
INSERT IGNORE INTO `users` (`username`, `password`, `enabled`, `nickname`) VALUES
    ('admin', '{noop}admin', TRUE, '管理员');

INSERT IGNORE INTO `user_roles` (`username`, `role_name`) VALUES
    ('admin', 'ADMIN');

-- Admin 自举：函数发布工作流
INSERT IGNORE INTO `wf_definition` (
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
);

-- Admin 自举：函数下线工作流
INSERT IGNORE INTO `wf_definition` (
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
);

-- Admin 自举：工作流定义发布
INSERT IGNORE INTO `wf_definition` (
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
);

-- Admin 自举：工作流定义下线
INSERT IGNORE INTO `wf_definition` (
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
);
