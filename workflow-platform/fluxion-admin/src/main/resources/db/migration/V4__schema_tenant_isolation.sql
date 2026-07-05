-- ============================================================
-- V4：为 wf_schema 增加多租户隔离字段（scope / app_group）
--     并补充细粒度编辑权限标识
-- ============================================================

-- ═══════════════════════════════════════════════════════════════════
-- 1. wf_schema 新增 scope + app_group 列
-- ═══════════════════════════════════════════════════════════════════

-- 1.1 新增 scope 列（若不存在）
SET @add_scope_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_schema` ADD COLUMN `scope` VARCHAR(16) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''作用域：PLATFORM/PRIVATE（列：scope）''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_schema'
      AND COLUMN_NAME = 'scope'
);

PREPARE stmt FROM @add_scope_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 新增 app_group 列（若不存在）
SET @add_app_group_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_schema` ADD COLUMN `app_group` VARCHAR(128) COMMENT ''所属应用分组（scope=PRIVATE 时必填）（列：app_group）''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_schema'
      AND COLUMN_NAME = 'app_group'
);

PREPARE stmt FROM @add_app_group_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.3 新增索引（若不存在）
SET @add_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_schema` ADD INDEX `idx_schema_scope_app_group` (`scope`, `app_group`)',
        'SELECT 1'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_schema'
      AND INDEX_NAME = 'idx_schema_scope_app_group'
);

PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ═══════════════════════════════════════════════════════════════════
-- 2. 补充细粒度编辑权限标识
--    schema:edit    - 允许编辑/创建 Schema
--    workflow:edit  - 允许编辑/创建工作流
--    function:edit  - 允许编辑/创建函数
--    ADMIN 角色默认拥有以上所有权限
-- ═══════════════════════════════════════════════════════════════════

INSERT IGNORE INTO `role_permissions` (`role_name`, `permission`) VALUES
    ('ADMIN', 'schema:edit'),
    ('ADMIN', 'workflow:edit'),
    ('ADMIN', 'function:edit');
