-- ============================================================
-- V6：为 wf_definition 增加 input_schema_format / output_schema_format 列
--      与 fluxion-schema 模块的 SchemaFormat 枚举对齐（json-schema / protobuf / avro）
--      列缺失会导致 JPA ddl-auto=validate 阶段直接失败：
--      Schema-validation: missing column [input_schema_format] in table [wf_definition]
-- ============================================================

-- ═══════════════════════════════════════════════════════════════════
-- 1. wf_definition 新增 input_schema_format 列（若不存在）
-- ═══════════════════════════════════════════════════════════════════

SET @add_input_format_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_definition` ADD COLUMN `input_schema_format` VARCHAR(32) NOT NULL DEFAULT ''json-schema'' COMMENT ''入参 Schema 格式标识：json-schema/protobuf/avro（列：input_schema_format）'' AFTER `input_schema`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_definition'
      AND COLUMN_NAME = 'input_schema_format'
);

PREPARE stmt FROM @add_input_format_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ═══════════════════════════════════════════════════════════════════
-- 2. wf_definition 新增 output_schema_format 列（若不存在）
-- ═══════════════════════════════════════════════════════════════════

SET @add_output_format_col_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `wf_definition` ADD COLUMN `output_schema_format` VARCHAR(32) NOT NULL DEFAULT ''json-schema'' COMMENT ''出参 Schema 格式标识：json-schema/protobuf/avro（列：output_schema_format）'' AFTER `output_schema`',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'wf_definition'
      AND COLUMN_NAME = 'output_schema_format'
);

PREPARE stmt FROM @add_output_format_col_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
