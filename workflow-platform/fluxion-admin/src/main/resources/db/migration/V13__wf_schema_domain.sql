-- ============================================================
-- V13：为 wf_schema 增加域分类（domain）字段
--
-- 与 wf_function.domain 取值对齐（db / redis / cache / http / mq /
-- script / common / other），用于在 Schema 管理页按领域分组展示，
-- 避免不同分类的 Schema 混合展示导致混乱。
--
-- 默认值 common：未指定领域的 Schema 归入「通用」分组，与函数列表
-- 的回退语义保持一致。
-- ============================================================

ALTER TABLE `wf_schema`
    ADD COLUMN `domain` VARCHAR(32) NOT NULL DEFAULT 'common'
    COMMENT '领域分类（db/redis/cache/http/mq/script/common/other）'
    AFTER `app_group`;

-- 已存在的内置函数配套 Schema 根据 schema_name 前缀打上正确的域。
UPDATE `wf_schema`
    SET `domain` = CASE
        WHEN schema_name LIKE 'builtin:%:param' AND schema_name LIKE '%redis%' THEN 'redis'
        WHEN schema_name LIKE 'builtin:%:param' AND schema_name LIKE '%db%'    THEN 'db'
        WHEN schema_name LIKE 'builtin:%:param' AND schema_name LIKE '%http%'  THEN 'http'
        WHEN schema_name LIKE 'builtin:%:param' AND (schema_name LIKE '%script%' OR schema_name LIKE '%groovy%') THEN 'script'
        WHEN schema_name LIKE 'builtin:%:param' THEN 'common'
        ELSE `domain`
    END
    WHERE schema_name LIKE 'builtin:%';
