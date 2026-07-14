-- ============================================================
-- V17：沙盒实例表（sandbox_instance）
--
-- 沙盒测试机制：用户点击「调试工作流」时自动为 session 创建一组沙盒
-- 资源实例。沙盒策略 = 在原始数据源连接的同一个实例上，以
-- `sandbox_{sessionId}_{原表名}` 作为临时表名前缀，执行
-- CREATE TABLE ... LIKE 克隆结构；Redis 沙盒则独占 dbIndex。
--
-- 这种方式兼容所有关系型数据库（不局限 MySQL），也不需要 CREATE
-- DATABASE 权限；调试结束或过期后自动 DROP 所有沙盒表 / FLUSHDB。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sandbox_instance` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `app_id`            BIGINT       NOT NULL COMMENT '所属应用 ID（FK -> app.id）',
    `base_resource_id`  BIGINT       NOT NULL COMMENT '基于哪个真实资源创建的沙盒（FK -> app_resource.id）',
    `sandbox_type`      VARCHAR(32)  NOT NULL COMMENT '策略：DB_TABLE_PREFIX / REDIS_DB_INDEX / KAFKA_TOPIC_PREFIX',
    `connection_config` JSON         NOT NULL COMMENT '沙盒上下文：{tablePrefix, redisDbIndex, topicPrefix, cleanupTargets:[...]}',
    `lifecycle`         VARCHAR(16)  NOT NULL DEFAULT 'CREATING' COMMENT 'CREATING/READY/IN_USE/EXPIRED/CLEANING/CLEANED/FAILED',
    `expired_at`        DATETIME     NOT NULL COMMENT '到期时间，Scheduler 每 10 分钟扫一次清理',
    `last_used_at`      DATETIME              COMMENT '最近一次访问时间，用于 LRU 提前回收',
    `owner_session`     VARCHAR(64)           COMMENT '归属调试会话 ID（由前端 DebugPanel 生成）',
    `owner_username`    VARCHAR(64)           COMMENT '创建人账号',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_app_lifecycle` (`app_id`, `lifecycle`),
    INDEX `idx_expired_lifecycle` (`expired_at`, `lifecycle`),
    INDEX `idx_owner_session` (`owner_session`),
    CONSTRAINT `fk_sandbox_app`      FOREIGN KEY (`app_id`)           REFERENCES `app`(`id`)          ON DELETE CASCADE,
    CONSTRAINT `fk_sandbox_resource` FOREIGN KEY (`base_resource_id`) REFERENCES `app_resource`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调试沙盒实例表（表前缀策略）';
