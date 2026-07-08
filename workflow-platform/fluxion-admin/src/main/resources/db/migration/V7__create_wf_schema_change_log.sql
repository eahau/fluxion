-- Flyway Migration: 创建 Schema 修改记录表（ChangeLog）
-- 由于 Schema 只有单份文件（无多版本），所有变更通过此表追踪。
-- 每次对 Schema 的 CRUD 操作自动记录一条或多条 ChangeLog。

CREATE TABLE IF NOT EXISTS `wf_schema_change_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `schema_name` VARCHAR(128) NOT NULL COMMENT 'Schema 名称（冗余存储，便于按 Schema 聚合查询）',
    `field_path`  VARCHAR(256) NOT NULL COMMENT '修改的字段路径，如 $.schemaJson.properties.age',
    `change_type` VARCHAR(32)  NOT NULL COMMENT '变更类型：CREATE / ADD / MODIFY / DEPRECATE / FREEZE / UNFREEZE / DELETE',
    `old_value`   TEXT                  COMMENT '变更前值（JSON 文本）',
    `new_value`   TEXT                  COMMENT '变更后值（JSON 文本）',
    `operator`    VARCHAR(128)          COMMENT '操作人',
    `reason`      VARCHAR(512)          COMMENT '变更原因说明',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_schema_name` (`schema_name`),
    KEY `idx_created_at`  (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Schema 修改记录（ChangeLog）';