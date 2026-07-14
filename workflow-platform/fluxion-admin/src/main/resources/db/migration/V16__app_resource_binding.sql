-- ============================================================
-- V16：应用-资源绑定表（app_resource_binding）
--
-- 将 app_resource 关联到具体的 app。同一个资源可以被多个 app
-- 绑定；同一个 app 下可以有多个同类型资源（比如订单服务同时绑
-- 一个读写库 + 一个只读库），通过 alias_in_app 区分。
--
-- resource_scope 给后端做软限制：READ_ONLY 资源不会让用户误选到
-- 执行 INSERT/UPDATE/DELETE 的节点（不过 DB 层账号权限才是硬保障）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `app_resource_binding` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `app_id`          BIGINT       NOT NULL COMMENT '应用 ID（FK -> app.id）',
    `resource_id`     BIGINT       NOT NULL COMMENT '资源 ID（FK -> app_resource.id）',
    `resource_scope`  VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT' COMMENT '资源范围：DEFAULT / READ_ONLY / WRITE_ONLY / CUSTOM',
    `alias_in_app`    VARCHAR(64)           COMMENT '在 App 内的引用别名，如 "default" / "analytics"；空 = 使用 resource_name',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_resource` (`app_id`, `resource_id`),
    INDEX `idx_app_id` (`app_id`),
    INDEX `idx_resource_id` (`resource_id`),
    CONSTRAINT `fk_arb_app`      FOREIGN KEY (`app_id`)      REFERENCES `app`(`id`)          ON DELETE CASCADE,
    CONSTRAINT `fk_arb_resource` FOREIGN KEY (`resource_id`) REFERENCES `app_resource`(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用-资源绑定表（N:M）';
