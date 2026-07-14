-- ============================================================
-- V15：资源注册表（app_resource）
--
-- 全局可复用的基础设施连接配置（DB / REDIS / MQ_KAFKA / MQ_ROCKETMQ /
-- DUBBO_REGISTRY 等）。敏感字段（password / saslJaas 等）在写入
-- config_json 前由后端 CryptService 使用 Jasypt 做 AES 加密。
--
-- 资源与应用是 N:M 关系：同一个 MySQL 集群可以被多个 App 绑定，
-- 每个 App 可为其赋予别名（alias_in_app）如 "default" / "readonly"。
-- ============================================================

CREATE TABLE IF NOT EXISTS `app_resource` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `resource_type` VARCHAR(16)  NOT NULL COMMENT '资源类型：DB / REDIS / KAFKA / ROCKETMQ / DUBBO_REGISTRY / NAMING',
    `resource_name` VARCHAR(64)  NOT NULL COMMENT '资源逻辑名，全局唯一，如 order-db / user-redis / prod-kafka',
    `driver`        VARCHAR(64)           COMMENT '驱动/实现：mysql8 / postgres15 / lettuce / redisson / sarama',
    `config_json`   JSON         NOT NULL COMMENT '连接配置（敏感字段已 Jasypt 加密）：{url, username, password_encrypted, ...}',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resource_name` (`resource_name`),
    INDEX `idx_type` (`resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='基础设施资源注册表（DB/Redis/MQ/...连接配置）';
