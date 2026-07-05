-- ============================================================
-- V5：为 wf_schema 增加 schema_format 列，支持多格式 Schema
--      （json-schema / protobuf / avro）
--      与 fluxion-schema 模块的 SchemaFormat 枚举对齐
-- ============================================================

-- ═══════════════════════════════════════════════════════════════════
-- 1. wf_schema 新增 schema_format 列
-- ═══════════════════════════════════════════════════════════════════

SET @add_format_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_schema` ADD COLUMN `schema_format` VARCHAR(32) NOT NULL DEFAULT ''json-schema'' COMMENT ''Schema 格式标识：json-schema/protobuf/avro（列：schema_format）'' AFTER `schema_type`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_schema'
      AND COLUMN_NAME = 'schema_format'
);

PREPARE stmt FROM @add_format_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
