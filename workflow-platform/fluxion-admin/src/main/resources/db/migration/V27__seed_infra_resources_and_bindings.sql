-- ============================================================
-- V27：预置本地基础设施资源（MySQL/Redis/Kafka）并绑定到 product-worker / order-worker 两个应用
--
-- 说明：
--   1) 预置 3 条本地开发环境常用资源（local-mysql / local-redis / local-kafka）
--      密码等敏感字段以明文写入（仅本地 seed 用途，生产环境请通过资源中心 UI 录入并走 Jasypt 加密）
--   2) 将 3 条资源分别绑定到 product-worker 和 order-worker 两个应用，
--      每个绑定的别名均为 'default'，读写权限 scope = READWRITE
--   3) 全部使用 REPLACE INTO，重复执行 Flyway 迁移时幂等；id 列不写，走表自增
-- ============================================================

SET NAMES utf8mb4;

-- =============================================================
-- Part A：往 app_resource 表 REPLACE INTO 3 条本地基础设施资源
-- =============================================================

-- 1. local-mysql：本地 MySQL8 开发库（fluxion 库）
REPLACE INTO `app_resource` (
    `resource_name`, `resource_type`, `driver`,
    `config_json`,
    `created_at`, `updated_at`
) VALUES (
    'local-mysql',
    'DB',
    'mysql8',
    '{"url":"jdbc:mysql://localhost:3306/fluxion?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true","username":"root","password":"password","driverClassName":"com.mysql.cj.jdbc.Driver","maxPoolSize":20,"minIdle":5}',
    NOW(),
    NOW()
);

-- 2. local-redis：本地 Redis 单实例（db0，无密码）
REPLACE INTO `app_resource` (
    `resource_name`, `resource_type`, `driver`,
    `config_json`,
    `created_at`, `updated_at`
) VALUES (
    'local-redis',
    'REDIS',
    'lettuce',
    '{"host":"localhost","port":6379,"password":"","database":0,"timeoutMs":3000,"maxPoolSize":16,"minIdle":4}',
    NOW(),
    NOW()
);

-- 3. local-kafka：本地 Kafka 单节点（localhost:9092）
REPLACE INTO `app_resource` (
    `resource_name`, `resource_type`, `driver`,
    `config_json`,
    `created_at`, `updated_at`
) VALUES (
    'local-kafka',
    'KAFKA',
    'kafka-clients',
    '{"bootstrapServers":"localhost:9092","acks":"all","retries":3,"consumerGroupPrefix":"${app.key}","enableAutoCommit":false,"sessionTimeoutMs":30000}',
    NOW(),
    NOW()
);

-- =============================================================
-- Part B：往 app_resource_binding 表 REPLACE INTO 6 条绑定
--          (local-mysql, local-redis, local-kafka) × (product-worker, order-worker)
-- =============================================================

-- local-mysql -> product-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'product-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-mysql'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);

-- local-redis -> product-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'product-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-redis'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);

-- local-kafka -> product-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'product-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-kafka'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);

-- local-mysql -> order-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'order-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-mysql'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);

-- local-redis -> order-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'order-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-redis'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);

-- local-kafka -> order-worker
REPLACE INTO `app_resource_binding` (
    `app_id`, `resource_id`, `resource_scope`, `alias_in_app`,
    `created_at`, `updated_at`
) VALUES (
    (SELECT `id` FROM `app` WHERE `app_key` = 'order-worker'),
    (SELECT `id` FROM `app_resource` WHERE `resource_name` = 'local-kafka'),
    'READWRITE',
    'default',
    NOW(),
    NOW()
);
