-- ============================================================
-- V2：合并以下两类 schema 修复
--   1. 为 wf_schema 增加 frozen 字段并补充 schema:unlock 权限
--   2. 修复 wf_definition 表 workflow_id 唯一约束缺失及重复数据
-- ============================================================

-- ═══════════════════════════════════════════════════════════════════
-- 1. wf_schema 冻结字段 & 解锁权限
-- ═══════════════════════════════════════════════════════════════════
-- 本地开发时 JPA ddl-auto=update 可能已经自动添加过该列，
-- 但 MySQL 9.x 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，
-- 因此通过 information_schema.COLUMNS 动态判断后执行。
SET @add_frozen_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_schema` ADD COLUMN `frozen` TINYINT(1) NOT NULL DEFAULT 0 COMMENT \'是否已冻结（锁定），冻结后普通用户不可修改/删除\'',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_schema'
      AND COLUMN_NAME = 'frozen'
);

PREPARE stmt FROM @add_frozen_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO `role_permissions` (`role_name`, `permission`) VALUES
    ('ADMIN', 'schema:unlock');

-- ═══════════════════════════════════════════════════════════════════
-- 2. wf_definition 去重 + 唯一/检查约束
-- ═══════════════════════════════════════════════════════════════════
-- 背景：
--   1. V1 已声明 UNIQUE KEY `uk_workflow_id` (`workflow_id`)，但早期
--      按 docs/workflow-ddl.sql（v2 设计稿）或手工建表时未包含该约束。
--   2. V1 使用 INSERT IGNORE 初始化 4 个 admin 元工作流；当唯一约束缺失时，
--      INSERT IGNORE 不会按 workflow_id 去重，导致重复插入。
-- 注意：本迁移假设 wf_definition 表已按 V1 创建（含 workflow_id 列）。
--       若表结构来自旧版 docs/workflow-ddl.sql（无 workflow_id 列），
--       请先按 V1 重建表并重新导入数据。
-- 修复动作：
--   1. 清理重复数据，仅保留 id 最小的一条；
--   2. 若不存在，则添加 uk_workflow_id 唯一约束；
--   3. 若不存在，则添加 chk_workflow_id_not_empty 检查约束。

-- 2.1 清理重复数据（按 workflow_id 分组，保留最小 id）
DELETE FROM `wf_definition`
WHERE `id` NOT IN (
    SELECT `min_id` FROM (
        SELECT MIN(`id`) AS `min_id` FROM `wf_definition` GROUP BY `workflow_id`
    ) AS `tmp`
);

-- 2.2 添加 workflow_id 唯一约束（若不存在）
SET @add_uk_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_definition` ADD UNIQUE KEY `uk_workflow_id` (`workflow_id`)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_definition'
      AND CONSTRAINT_NAME = 'uk_workflow_id'
);

PREPARE stmt FROM @add_uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 添加 workflow_id 非空检查约束（若不存在）
SET @add_check_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_definition` ADD CONSTRAINT `chk_workflow_id_not_empty` CHECK (`workflow_id` <> \'\')',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_definition'
      AND CONSTRAINT_NAME = 'chk_workflow_id_not_empty'
);

PREPARE stmt FROM @add_check_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
