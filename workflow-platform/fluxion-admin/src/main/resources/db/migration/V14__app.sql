-- ============================================================
-- V14：应用主表（app）
--
-- 替换原 wf_definition.app_group 散列字符串为实体化的应用对象，
-- 支持 owner、status、描述、独立的对外展示名称，以及后续资源
-- 绑定、沙盒管理、发布权限等能力全部围绕 app 展开。
--
-- 迁移后：wf_definition.app_group = app.app_key（保持兼容查询）
--         wf_definition.app_id = app.id（新增外键）
-- ============================================================

CREATE TABLE IF NOT EXISTS `app` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `app_key`       VARCHAR(64)  NOT NULL COMMENT '应用唯一标识（对应原 app_group），如 order-service',
    `app_name`      VARCHAR(128) NOT NULL COMMENT '应用展示名称，如 订单服务',
    `description`   VARCHAR(512)          COMMENT '应用描述说明',
    `owner`         VARCHAR(64)           COMMENT '负责人账号',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE/DISABLED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_key` (`app_key`),
    INDEX `idx_status` (`status`),
    INDEX `idx_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用主表（多租户隔离根对象）';

-- 从已有的 wf_definition / wf_schema / wf_function / user_app_groups 的
-- app_group 列聚合出全部已在使用的应用，初始化到 app 表，保证后续新增
-- app_id 外键时有数据可关联。
INSERT IGNORE INTO `app` (`app_key`, `app_name`, `description`, `created_at`, `updated_at`)
SELECT DISTINCT src.app_group, src.app_group, '迁移自 app_group 字段', NOW(), NOW()
FROM (
    SELECT `app_group` FROM `wf_definition` WHERE `app_group` IS NOT NULL AND `app_group` <> ''
    UNION
    SELECT `app_group` FROM `wf_schema`     WHERE `app_group` IS NOT NULL AND `app_group` <> ''
    UNION
    SELECT `app_group` FROM `wf_function`   WHERE `app_group` IS NOT NULL AND `app_group` <> ''
    UNION
    SELECT `app_group` FROM `user_app_groups` WHERE `app_group` IS NOT NULL AND `app_group` <> ''
) src;

-- 兜底（若上面都没有数据，至少插入两个占位 app 让本地测试可用）
INSERT IGNORE INTO `app` (`app_key`, `app_name`, `description`, `owner`) VALUES
    ('admin',          'Fluxion 管理控制台',     '内置应用：fluxion-admin 自身工作流与函数', 'system'),
    ('test-worker',    '测试 Worker 应用',       '默认内置应用：test-worker 工作流组',       'system');
