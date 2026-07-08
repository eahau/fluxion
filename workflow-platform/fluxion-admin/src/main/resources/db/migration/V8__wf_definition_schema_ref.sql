-- Flyway Migration: 在工作流定义表中增加入/出参 Schema 引用名称
-- 用于持久化工作流元信息配置中 Schema 下拉框的已选项，避免每次打开抽屉都需按内容反查。
ALTER TABLE `wf_definition`
    ADD COLUMN `input_schema_ref`  VARCHAR(128) COMMENT '入参 Schema 引用名称' AFTER `input_schema_format`,
    ADD COLUMN `output_schema_ref` VARCHAR(128) COMMENT '出参 Schema 引用名称' AFTER `output_schema_format`;
