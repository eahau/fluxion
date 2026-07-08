-- ============================================================
-- V10：为 wf_function 表新增 category 列（前端能力分组）
--   与 functionType（BUILTIN/SCRIPT/EXTERNAL/CUSTOM）正交，
--   对应前端 FUNCTION_GROUPS：flow-control/data-access/data-processing/
--   validation/response/script/custom/external/other。
--   NULLABLE 加列，MySQL 8 / PG 均为瞬时 Online DDL，不锁表、可重入。
-- ============================================================

ALTER TABLE `wf_function`
    ADD COLUMN `category` VARCHAR(32) NULL
    COMMENT '前端能力分组 key，对应 FUNCTION_GROUPS：flow-control/data-access/data-processing/validation/response/script/custom/external/other';
