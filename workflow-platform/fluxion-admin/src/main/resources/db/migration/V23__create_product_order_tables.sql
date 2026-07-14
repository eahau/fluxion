-- ============================================================
-- V23：创建商品(product)、订单(order_main)、订单项(order_item)三张业务表
--      + 预置 product-worker / order-worker 两个应用实体(app 表)
--
-- 业务表全部与 wf_definition 同库（fluxion 库），简化 builtin:dbExecute
-- 的默认数据源路由；后续如需独立库，可在 builtin:dbExecute 参数中
-- 传 dataSource="xxx" 并在 Worker 侧配置多数据源。
--
-- 两个 app 实体对应 Runtime 的 workflow.instance.app-group，
-- 后续 V24/V25 中 wf_schema.app_id、wf_definition.app_id、wf_function.app_id
-- 均通过 app.app_key 关联过来，保证 V18 引入的 NOT NULL 外键满足。
-- ============================================================

-- ───────────────────────────────────────────────────────────
-- 1. 商品表：product
-- ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品主键',
    `product_no`  VARCHAR(64)   NOT NULL COMMENT '商品编号（业务唯一）',
    `name`        VARCHAR(256)  NOT NULL COMMENT '商品名称',
    `description` TEXT          NULL COMMENT '商品描述',
    `category`    VARCHAR(64)   NULL COMMENT '商品分类',
    `price`       DECIMAL(14,2) NOT NULL COMMENT '销售单价',
    `stock`       INT           NOT NULL DEFAULT 0 COMMENT '库存数量',
    `status`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '上架状态：1=上架，0=下架（软删除）',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_no` (`product_no`),
    KEY `idx_product_category` (`category`),
    KEY `idx_product_status`   (`status`),
    KEY `idx_product_created`  (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表';

-- ───────────────────────────────────────────────────────────
-- 2. 订单主表：order_main
--    注意：表名使用 order_main 避免和 SQL 保留字 order 冲突。
-- ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `order_main` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    `order_no`     VARCHAR(64)   NOT NULL COMMENT '订单编号（业务唯一）',
    `user_id`      BIGINT        NOT NULL COMMENT '下单用户ID',
    `total_amount` DECIMAL(14,2) NOT NULL COMMENT '订单总额（商品原价合计）',
    `pay_amount`   DECIMAL(14,2) NOT NULL COMMENT '实付金额（优惠后）',
    `status`       VARCHAR(16)   NOT NULL DEFAULT 'CREATED' COMMENT '订单状态：CREATED/PAID/SHIPPED/COMPLETED/CANCELLED',
    `remark`       VARCHAR(512)  NULL COMMENT '订单备注',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no`       (`order_no`),
    KEY `idx_order_user_id`        (`user_id`),
    KEY `idx_order_status`         (`status`),
    KEY `idx_order_created_at`     (`created_at`),
    KEY `idx_order_user_status`    (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- ───────────────────────────────────────────────────────────
-- 3. 订单项表：order_item
-- ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单项主键',
    `order_id`     BIGINT        NOT NULL COMMENT '所属订单ID（FK -> order_main.id）',
    `product_id`   BIGINT        NOT NULL COMMENT '商品ID（FK -> product.id）',
    `product_name` VARCHAR(256)  NOT NULL COMMENT '下单时的商品名称快照',
    `price`        DECIMAL(14,2) NOT NULL COMMENT '下单时的商品单价快照',
    `quantity`     INT           NOT NULL COMMENT '购买数量',
    `subtotal`     DECIMAL(14,2) NOT NULL COMMENT '小计金额（price * quantity）',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order_id`   (`order_id`),
    KEY `idx_order_item_product_id` (`product_id`),
    CONSTRAINT `fk_order_item_order`
        FOREIGN KEY (`order_id`) REFERENCES `order_main` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_order_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项明细表';

-- ───────────────────────────────────────────────────────────
-- 4. 预置 app 实体：product-worker / order-worker
--    这两个 app_key 对应 Runtime 启动时的
--    workflow.instance.app-group 配置值。
--    使用 INSERT IGNORE + uk_app_key 保证幂等。
-- ───────────────────────────────────────────────────────────
INSERT IGNORE INTO `app`
    (`app_key`, `app_name`, `description`, `owner`, `status`, `created_at`, `updated_at`)
VALUES
    ('product-worker', '商品服务 Worker',  '商品域 CRUD 函数集合执行节点：商品创建/查询/更新/删除/分页列表', 'system', 'ACTIVE', NOW(), NOW()),
    ('order-worker',   '订单服务 Worker',  '订单域 CRUD 函数集合执行节点：订单创建/查询/状态流转/取消/分页列表', 'system', 'ACTIVE', NOW(), NOW());
