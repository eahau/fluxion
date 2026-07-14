-- ============================================================
-- V18：为 wf_definition / wf_schema / wf_function 增加 app_id 外键
--
-- 保持 app_group 列（作为 app.app_key 的冗余列便于索引与查询），
-- 新增 app_id BIGINT 作为到 app 表的正式强外键关联。
-- 项目处于开发阶段，一次性迁移不做兼容过渡期。
-- ============================================================

-- ───────────────────────────────────────────────────────────
-- wf_definition
-- ───────────────────────────────────────────────────────────
ALTER TABLE `wf_definition`
    ADD COLUMN `app_id` BIGINT COMMENT '所属应用 ID（FK -> app.id）'
    AFTER `app_group`;

UPDATE `wf_definition` wd
    INNER JOIN `app` a ON a.`app_key` = wd.`app_group`
    SET wd.`app_id` = a.`id`
    WHERE wd.`app_group` IS NOT NULL AND wd.`app_group` <> '';

-- 对于 scope=PLATFORM 的工作流（app_group=NULL），默认归属 admin 应用
UPDATE `wf_definition` wd
    INNER JOIN `app` a ON a.`app_key` = 'admin'
    SET wd.`app_id` = a.`id`
    WHERE wd.`app_id` IS NULL;

ALTER TABLE `wf_definition`
    MODIFY COLUMN `app_id` BIGINT NOT NULL,
    ADD INDEX `idx_app_id` (`app_id`),
    ADD CONSTRAINT `fk_wfdef_app` FOREIGN KEY (`app_id`) REFERENCES `app`(`id`) ON DELETE RESTRICT;

-- ───────────────────────────────────────────────────────────
-- wf_schema
-- ───────────────────────────────────────────────────────────
ALTER TABLE `wf_schema`
    ADD COLUMN `app_id` BIGINT COMMENT '所属应用 ID（FK -> app.id）'
    AFTER `app_group`;

UPDATE `wf_schema` ws
    INNER JOIN `app` a ON a.`app_key` = ws.`app_group`
    SET ws.`app_id` = a.`id`
    WHERE ws.`app_group` IS NOT NULL AND ws.`app_group` <> '';

UPDATE `wf_schema` ws
    INNER JOIN `app` a ON a.`app_key` = 'admin'
    SET ws.`app_id` = a.`id`
    WHERE ws.`app_id` IS NULL;

ALTER TABLE `wf_schema`
    MODIFY COLUMN `app_id` BIGINT NOT NULL,
    ADD INDEX `idx_app_id` (`app_id`),
    ADD CONSTRAINT `fk_wfschema_app` FOREIGN KEY (`app_id`) REFERENCES `app`(`id`) ON DELETE RESTRICT;

-- ───────────────────────────────────────────────────────────
-- wf_function
-- ───────────────────────────────────────────────────────────
ALTER TABLE `wf_function`
    ADD COLUMN `app_id` BIGINT COMMENT '所属应用 ID（FK -> app.id）'
    AFTER `app_group`;

UPDATE `wf_function` wf
    INNER JOIN `app` a ON a.`app_key` = wf.`app_group`
    SET wf.`app_id` = a.`id`
    WHERE wf.`app_group` IS NOT NULL AND wf.`app_group` <> '';

UPDATE `wf_function` wf
    INNER JOIN `app` a ON a.`app_key` = 'admin'
    SET wf.`app_id` = a.`id`
    WHERE wf.`app_id` IS NULL;

ALTER TABLE `wf_function`
    MODIFY COLUMN `app_id` BIGINT NOT NULL,
    ADD INDEX `idx_app_id` (`app_id`),
    ADD CONSTRAINT `fk_wffunc_app` FOREIGN KEY (`app_id`) REFERENCES `app`(`id`) ON DELETE RESTRICT;
