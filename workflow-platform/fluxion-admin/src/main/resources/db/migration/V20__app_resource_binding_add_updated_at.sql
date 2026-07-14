-- ============================================================
-- V20：修复 app_resource_binding 表缺失 updated_at 列
--
-- 背景：V16 建表时只写了 created_at，但是 AppResourceBinding Entity
-- 继承了 BaseEntity，后者强制声明了 updated_at 列（用于 JPA ddl-auto:validate）。
--
-- 变更：
--   * 新增 updated_at 列（DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP）
--   * 加上 ON UPDATE CURRENT_TIMESTAMP 自动刷新（与 BaseEntity 中 @PreUpdate 并行工作，
--     即使绕过 JPA 直接写 SQL 也能保证时间戳更新）
-- ============================================================

ALTER TABLE `app_resource_binding`
    ADD COLUMN `updated_at` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间'
    AFTER `created_at`;
