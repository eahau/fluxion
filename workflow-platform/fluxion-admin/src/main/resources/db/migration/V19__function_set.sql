-- ========================================================================
-- V19__function_set.sql
--
-- 将 wf_definition 从「必须绑定触发协议的工作流」放宽为「可发布为可复用
-- 函数集合的 DAG」：
--   1. protocol 列放宽为 nullable（无触发入口的 set 就是纯业务集合）
--   2. 新增 is_published_set / set_ref_name 两列支持对外发布成函数
--
-- 兼容策略 = 一次性到位（用户选择不做兼容）：
--   - 旧数据保留 protocol 原值；新建 wf_definition 不再强制 HTTP/MQ/RPC
--   - is_published_set 默认 0（不发布）= 跟旧行为一致
-- ========================================================================

-- 1. wf_definition.protocol 改为 nullable（纯业务集合可以没触发入口）
ALTER TABLE wf_definition
    MODIFY COLUMN protocol VARCHAR(32) NULL COMMENT 'Binding protocol: HTTP / DUBBO / GRPC / KAFKA / NULL (untriggered set)';

-- 2. 是否作为「可复用函数集合」发布（0/1）。默认 0 = 不发布
ALTER TABLE wf_definition
    ADD COLUMN is_published_set TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 = this DAG is exposed as a callable function set'
    AFTER status;

-- 3. set_ref_name = 发布后作为 functionName 被其它 DAG 引用。发布时才非空。
ALTER TABLE wf_definition
    ADD COLUMN set_ref_name VARCHAR(128) NULL COMMENT 'Unique stable function-name used when this DAG is referenced as a set inside another DAG'
    AFTER is_published_set;

-- 4. set_ref_name 全局唯一（一个名字只能对应一个已发布的 set）
ALTER TABLE wf_definition
    ADD UNIQUE KEY uk_set_ref_name (set_ref_name);

-- 5. 给 wf_function.function_type 的合法取值范围增加一个注释：新增 SET_REF 类型
--    （function_type 是 VARCHAR，无 CHECK 约束，Java 侧限制即可）
ALTER TABLE wf_function
    MODIFY COLUMN function_type VARCHAR(32) NOT NULL COMMENT 'BUILTIN / SCRIPT_GROOVY / SCRIPT_JS / JAVA_CLASS / EXTERNAL / CUSTOM / SET_REF';
