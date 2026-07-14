-- ============================================================
-- V21：补齐 sandbox_instance 表的 updated_at 列
--
-- 背景：SandboxInstance 实体继承自 BaseEntity（BaseEntity 同时
-- 定义了 created_at 和 updated_at 两个时间戳列，并在 @PreUpdate
-- 时自动写入 updated_at）。
--
-- 但 V17 的初始建表 SQL 只写了 created_at，漏掉了 updated_at，
-- 导致 Hibernate validate 模式启动时报：
--   Schema-validation: missing column [updated_at] in table [sandbox_instance]
--
-- 这里用 ALTER 方式新增该列，默认值=当前时间（对已有数据统一
-- 回填），并开启 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 自动刷新，
-- 与 BaseEntity 中代码层的 @PreUpdate 双保险（即便 JPA 侧没写，
-- DB 层也会在每一次 UPDATE 时自动把时间戳刷新）。
-- ============================================================

ALTER TABLE `sandbox_instance`
    ADD COLUMN `updated_at` DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间'
    AFTER `created_at`;
