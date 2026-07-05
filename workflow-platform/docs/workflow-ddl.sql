-- ============================================================
--  函数式工作流平台 v2 — DDL 最终版
--  对应技术方案：workflow-v2-design.md § 13
-- ============================================================

-- ── 工作流定义 ────────────────────────────────────────────────
-- workflow_id 为业务唯一标识；bind_key 对应 HTTP path / Dubbo serviceKey / Kafka topic
-- is_protected=1 表示核心元工作流，禁止删除、禁止修改路由配置
CREATE TABLE wf_definition (
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id               VARCHAR(64)  NOT NULL COMMENT '工作流业务唯一标识',
    workflow_name             VARCHAR(128) NOT NULL COMMENT '工作流显示名称',
    dag_json                  TEXT         NOT NULL COMMENT 'DAG 节点定义（JSON格式）',
    input_schema              TEXT                  COMMENT '入参 Schema 内容（可选）',
    input_schema_format       VARCHAR(32)  NOT NULL DEFAULT 'json-schema' COMMENT '入参 Schema 格式：json-schema/protobuf/avro',
    output_schema             TEXT                  COMMENT '出参 Schema 内容（可选）',
    output_schema_format      VARCHAR(32)  NOT NULL DEFAULT 'json-schema' COMMENT '出参 Schema 格式：json-schema/protobuf/avro',
    error_handler_ref         VARCHAR(128)          COMMENT '错误处理函数引用（可选）',
    category                  VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS' COMMENT '工作流分类：BUSINESS/META',
    is_protected              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=受保护元工作流，禁止删除',
    scope                     VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PLATFORM/PRIVATE/MARKETPLACE',
    app_group                 VARCHAR(128)          COMMENT '所属应用分组（scope=PRIVATE 时必填）',
    source_ref                VARCHAR(128)          COMMENT '来源引用（安装市场工作流时指向原始 workflowId）',
    protocol                  VARCHAR(32)  NOT NULL DEFAULT 'HTTP' COMMENT '协议：HTTP/DUBBO/GRPC/KAFKA/ALL',
    method                    VARCHAR(20)           COMMENT '协议方法（HTTP方法/gRPC调用类型/Dubbo方法名）',
    bind_key                  VARCHAR(256)          COMMENT '协议绑定键（HTTP=path, Dubbo=serviceKey, Kafka=topic）',
    target_groups             VARCHAR(1024)         COMMENT '发布目标应用群组（JSON数组，NULL=全量广播）',
    publish_target            VARCHAR(1024)         COMMENT '发布目标（JSON对象，包含 type/groups/instanceIds）',
    triggers_config           TEXT                  COMMENT '触发器配置（JSON数组存储 WorkflowTrigger 列表）',
    version                   INT          NOT NULL DEFAULT 1,
    status                    VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/DEPRECATED',
    transaction_mode          VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SAGA',
    workflow_decorators       TEXT                  COMMENT '工作流级装饰器列表（JSON字符串数组）',
    workflow_decorator_params JSON                  COMMENT '工作流级装饰器参数（key=装饰器名，value=参数Map）',
    created_by                VARCHAR(64),
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- workflow_id 全局唯一；INSERT IGNORE / ON DUPLICATE KEY UPDATE 依赖此约束实现幂等
    UNIQUE KEY uk_workflow_id (workflow_id),
    KEY idx_protocol_bind (protocol, bind_key(128)),
    KEY idx_scope_route (scope, app_group, protocol, method, bind_key(128)),
    KEY idx_status (status),
    CONSTRAINT chk_workflow_id_not_empty CHECK (workflow_id <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表';

-- ── 工作流版本快照 ─────────────────────────────────────────────
-- 每次发布 / 修改受保护元工作流时，记录完整定义快照，支持版本对比和回滚
CREATE TABLE wf_version_snapshot (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id   BIGINT       NOT NULL,
    version         INT          NOT NULL,
    snapshot        JSON         NOT NULL COMMENT '完整 WorkflowDefinition 序列化',
    change_summary  VARCHAR(512)          COMMENT '变更摘要（发布时填写）',
    created_by      VARCHAR(64),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_definition_version (definition_id, version),
    INDEX idx_definition_created (definition_id, created_at)
);

-- ── 函数注册表 ────────────────────────────────────────────────
-- BUILTIN：引擎内置，此表仅存元信息（name/schema），实现由代码提供
-- CUSTOM：代码注册，此表存元信息，重启后 Spring Bean 自动注册
-- SCRIPT：脚本函数，config.scriptBody 存储脚本内容，引擎启动时加载编译
-- EXTERNAL：外部服务，config.endpoint 存 gRPC/HTTP 地址，引擎通过 Function Gateway 调用
--
-- 采用「混合模式」：保留高频检索/约束列，其余配置统一收敛到 config JSON，
-- 新增配置项无需修改数据库表结构。
CREATE TABLE wf_function (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    function_name VARCHAR(128) NOT NULL COMMENT '函数引用名（如 builtin:dbQuery）',
    function_type VARCHAR(32)  NOT NULL DEFAULT 'BUILTIN'
                  COMMENT '类型: BUILTIN / SCRIPT_GROOVY / SCRIPT_JS / JAVA_CLASS / EXTERNAL / CUSTOM',
    scope         VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE'
                  COMMENT '作用域: PLATFORM(平台内置) / PRIVATE(应用私有) / MARKETPLACE(市场)',
    app_group     VARCHAR(128)           COMMENT '所属应用分组（scope=PRIVATE 时必填）',
    source_ref    VARCHAR(128)           COMMENT '来源引用（安装市场函数时指向原始 functionName）',
    domain        VARCHAR(32)            COMMENT '函数所属领域（db/http/redis/cache/common/script），用于筛选和专用编辑器',
    config        JSON                   COMMENT '统一配置 JSON（description/paramSchema/outputSchema/scriptBody/className/publishTarget 等）',
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- scope + app_group 隔离后，不同 app_group 可复用相同函数名
    UNIQUE KEY uk_scope_app_function (scope, app_group, function_name),
    INDEX idx_type_status (function_type, status),
    INDEX idx_domain (domain)
);

-- ── 执行日志（可选，默认输出到结构化 Log）─────────────────────
-- ⚠️  此表为可选组件，默认不启用。
--     默认行为：WorkflowEngine 通过 LoggingExecutionLogStore（SPI）
--               将执行记录输出为结构化日志（JSON），接入 ELK/Loki。
--     启用 DB 持久化：application.yml 配置
--               workflow.execution-log.store=db
--     DB 持久化适用场景：
--       - 需要精确节点重放（DebugService.rerunFromNode）
--       - 需要 Admin UI 查询历史执行轨迹
--       - 需要 Saga 补偿审计（side_effects 字段）
-- 注意：每次工作流执行对应多条记录（每个节点一条 + 工作流级汇总一条）
CREATE TABLE wf_execution_log (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id     VARCHAR(64)  NOT NULL COMMENT 'UUID，单次工作流执行唯一标识',
    workflow_id      BIGINT       NOT NULL,
    workflow_version INT          NOT NULL COMMENT '执行时锁定的版本（ImmutableState 快照）',
    node_id          VARCHAR(64)           COMMENT 'null=工作流级汇总记录',
    node_name        VARCHAR(128),
    status           ENUM('SUCCESS','FAILED','SKIPPED','FALLBACK','RETRY') NOT NULL,
    input_data       JSON                  COMMENT 'NodeInput 快照（调试/重放用，可按需关闭）',
    output_data      JSON                  COMMENT '节点输出',
    side_effects     JSON                  COMMENT 'FunctionResult.sideEffects（Saga 审计）',
    error_msg        TEXT,
    duration_ms      INT,
    retry_attempt    TINYINT DEFAULT 0     COMMENT '当前记录是第几次重试（0=首次）',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution_id        (execution_id),
    INDEX idx_workflow_created    (workflow_id, created_at),
    INDEX idx_execution_node      (execution_id, node_id),
    INDEX idx_workflow_status_time (workflow_id, status, created_at)
);

-- ── Schema 管理 ───────────────────────────────────────────────
-- 独立存储 JSON Schema，可跨工作流复用
-- wf_definition.input_schema/output_schema 可内联或引用此表（$ref:schema:<name>）
CREATE TABLE wf_schema (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL UNIQUE COMMENT '引用名（如 order-create-request）',
    version       INT          NOT NULL DEFAULT 1,
    schema_type   ENUM('INPUT','OUTPUT','SHARED') NOT NULL,
    schema_format VARCHAR(32)  NOT NULL DEFAULT 'json-schema' COMMENT 'Schema 格式：json-schema/protobuf/avro',
    json_schema   JSON         NOT NULL,
    description   VARCHAR(512),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ── 通用资源模板注册表 ────────────────────────────────────────
-- 抽象各类具名资源模板：Redis Key 命名规范、MQ Topic 配置、HTTP 端点、DB 表别名等
-- 替代原 wf_redis_key_schema（过于具体），统一由 ResourceTemplateRegistry 管理
--
-- 使用示例（nodeParams 中引用）：
--   Redis Key:    {"resourceRef": "user:session", "userId": "${wfInput.userId}"}
--   MQ Topic:     {"resourceRef": "order:created"}
--   HTTP 端点:    {"resourceRef": "payment:charge"}
--
-- resource_type 枚举值（可扩展，存 VARCHAR 不用 ENUM）：
--   REDIS_KEY     — Redis Key 模板，config.ttlSeconds 指定 TTL
--   MQ_TOPIC      — 消息队列 Topic，config.groupId / config.partitions
--   HTTP_ENDPOINT — 外部 HTTP 服务端点，config.timeoutMs / config.retryCount
--   DB_TABLE      — 动态 SQL 表别名（跨库路由时使用）
CREATE TABLE wf_resource_template (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(128) NOT NULL UNIQUE COMMENT '资源引用名（如 user:session、order:created）',
    resource_type VARCHAR(32)  NOT NULL          COMMENT 'REDIS_KEY / MQ_TOPIC / HTTP_ENDPOINT / DB_TABLE',
    template      VARCHAR(512) NOT NULL          COMMENT '模板字符串，{placeholder} 在运行时由 NodeInput 解析',
    config        JSON                           COMMENT '类型相关配置（ttlSeconds / timeoutMs / partitions 等）',
    description   VARCHAR(512),
    enabled       TINYINT DEFAULT 1,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_type_enabled (resource_type, enabled)
);

-- ── 工作流市场 ───────────────────────────────────────────────
-- 市场发布记录（工作流/函数均可发布）
CREATE TABLE wf_marketplace_listing (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    listing_id    VARCHAR(128) NOT NULL UNIQUE COMMENT '市场 ID（如 user-login-flow）',
    source_type   ENUM('WORKFLOW','FUNCTION') NOT NULL,
    source_id     VARCHAR(128) NOT NULL COMMENT '源工作流/函数 ID',
    source_version INT NOT NULL COMMENT '发布时锁定的版本',
    title         VARCHAR(256) NOT NULL,
    description   TEXT,
    tags          JSON COMMENT '标签列表',
    author        VARCHAR(64) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DEPRECATED',
    install_count INT DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source (source_type, source_id),
    INDEX idx_status (status)
);

-- 市场安装记录
CREATE TABLE wf_marketplace_install (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    listing_id    VARCHAR(128) NOT NULL,
    app_group     VARCHAR(128) NOT NULL,
    installed_by  VARCHAR(64),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_listing_app (listing_id, app_group)
);

-- ── 用户-应用分组关联（多租户隔离）──────────────────────
CREATE TABLE user_app_groups (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(64)  NOT NULL COMMENT '用户名',
    app_group  VARCHAR(128) NOT NULL COMMENT '应用分组',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_app_group (username, app_group),
    INDEX idx_username (username),
    INDEX idx_app_group (app_group),
    CONSTRAINT fk_uag_user FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);
