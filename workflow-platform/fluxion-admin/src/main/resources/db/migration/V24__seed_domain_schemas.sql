-- ============================================================
-- V24：Seed 商品域 & 订单域 JSON Schema（共 12 条）
--
-- wf_schema 字段确认（对齐 V1/V2/V4/V5/V13/V18 迁移）：
--   schema_name        VARCHAR(128) UNIQUE NOT NULL
--   schema_type        VARCHAR(64)     DEFAULT 'INPUT'（可逗号拼接 INPUT,OUTPUT）
--   schema_format      VARCHAR(32)     DEFAULT 'json-schema'
--   schema_json        TEXT        NOT NULL（JSON Schema 内容）
--   description        VARCHAR(512)
--   scope              VARCHAR(16) NOT NULL DEFAULT 'PLATFORM'
--   app_group          VARCHAR(128)（scope=PRIVATE 时必填 = app.app_key）
--   app_id             BIGINT NOT NULL FK -> app.id
--   domain             VARCHAR(32)     DEFAULT 'common'（db/product db/order）
--   frozen             TINYINT(1)      DEFAULT 0（1=冻结，用户不可随意编辑，此处统一 frozen=1）
--
-- 使用 INSERT IGNORE + uk_schema_name 保证幂等。
-- app_id 通过嵌套子查询从 app 表按 app_key 映射，避免 V18 FK NOT NULL 报错。
-- JSON Schema 统一使用 draft-07 规范，与 fluxion-schema JsonSchemaValidator 对齐。
-- ============================================================

-- ───────────────────────────────────────────────────────────
-- 一、商品域 Schema（scope=PRIVATE，app_group=product-worker，domain=db/product）
-- ───────────────────────────────────────────────────────────

-- 1. 商品创建入参
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:create:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductCreateParam",
      "description": "创建商品入参",
      "additionalProperties": false,
      "required": ["product_no", "name", "price"],
      "properties": {
        "product_no":  { "type": "string", "minLength": 1, "maxLength": 64, "description": "商品编号" },
        "name":        { "type": "string", "minLength": 1, "maxLength": 256, "description": "商品名称" },
        "description": { "type": "string", "maxLength": 65535, "description": "商品描述（可选）" },
        "category":    { "type": "string", "maxLength": 64, "description": "商品分类（可选）" },
        "price":       { "type": "number", "minimum": 0, "exclusiveMinimum": false, "description": "商品单价，支持小数" },
        "stock":       { "type": "integer", "minimum": 0, "default": 0, "description": "库存，默认 0" },
        "status":      { "type": "integer", "enum": [0, 1], "default": 1, "description": "上架状态：0=下架 1=上架" }
      }
    }',
    '商品创建入参 Schema（product-worker 域专用）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- 2. 商品更新入参（Patch 语义，主键必填，其余选填）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:update:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductUpdateParam",
      "additionalProperties": false,
      "required": ["id"],
      "minProperties": 2,
      "properties": {
        "id":          { "type": "integer", "minimum": 1, "description": "商品主键（必填）" },
        "product_no":  { "type": "string", "minLength": 1, "maxLength": 64 },
        "name":        { "type": "string", "minLength": 1, "maxLength": 256 },
        "description": { "type": "string", "maxLength": 65535 },
        "category":    { "type": "string", "maxLength": 64 },
        "price":       { "type": "number", "minimum": 0 },
        "stock":       { "type": "integer", "minimum": 0 },
        "status":      { "type": "integer", "enum": [0, 1] }
      }
    }',
    '商品更新入参 Schema（Patch 语义，必须至少含 1 个业务字段）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- 3. 商品单条查询入参（按 id 或 product_no）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:get:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductGetParam",
      "additionalProperties": false,
      "oneOf": [
        { "required": ["id"] },
        { "required": ["product_no"] }
      ],
      "properties": {
        "id":         { "type": "integer", "minimum": 1, "description": "商品主键" },
        "product_no": { "type": "string", "minLength": 1, "maxLength": 64, "description": "商品编号" }
      }
    }',
    '商品单条查询入参（id 与 product_no 二选一）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- 4. 商品列表入参（limit/offset）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:list:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductListParam",
      "additionalProperties": false,
      "properties": {
        "category": { "type": "string", "maxLength": 64, "description": "分类过滤" },
        "status":   { "type": "integer", "enum": [0, 1], "description": "上架状态过滤" },
        "keyword":  { "type": "string", "minLength": 1, "maxLength": 128, "description": "名称/编号模糊搜索" },
        "limit":    { "type": "integer", "minimum": 1, "maximum": 500, "default": 20 },
        "offset":   { "type": "integer", "minimum": 0, "default": 0 }
      }
    }',
    '商品列表查询入参（支持分类、状态、关键词 + limit/offset）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- 5. 商品分页入参（pageNo/pageSize）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:page:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductPageParam",
      "additionalProperties": false,
      "required": ["pageNo", "pageSize"],
      "properties": {
        "category": { "type": "string", "maxLength": 64 },
        "status":   { "type": "integer", "enum": [0, 1] },
        "keyword":  { "type": "string", "minLength": 1, "maxLength": 128 },
        "pageNo":   { "type": "integer", "minimum": 1, "default": 1 },
        "pageSize": { "type": "integer", "minimum": 1, "maximum": 200, "default": 20 }
      }
    }',
    '商品分页查询入参（必须提供 pageNo & pageSize）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- 6. 商品通用出参（与 product 表字段一致 + 返回字段说明）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'product:result', 'OUTPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "ProductResult",
      "additionalProperties": true,
      "properties": {
        "id":          { "type": "integer" },
        "product_no":  { "type": "string" },
        "name":        { "type": "string" },
        "description": { "type": "string" },
        "category":    { "type": "string" },
        "price":       { "type": "number" },
        "stock":       { "type": "integer" },
        "status":      { "type": "integer" },
        "created_at":  { "type": ["string", "null"] },
        "updated_at":  { "type": ["string", "null"] }
      }
    }',
    '商品出参 Schema（单条查询 & 列表元素均复用）',
    'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key = 'product-worker' LIMIT 1),
    'db/product', 1, NOW(), NOW()
);

-- ───────────────────────────────────────────────────────────
-- 二、订单域 Schema（scope=PRIVATE，app_group=order-worker，domain=db/order）
-- ───────────────────────────────────────────────────────────

-- 7. 订单创建入参（用户 + items[]）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:create:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderCreateParam",
      "additionalProperties": false,
      "required": ["user_id", "items"],
      "properties": {
        "user_id": { "type": "integer", "minimum": 1, "description": "下单用户ID" },
        "remark":  { "type": "string", "maxLength": 512, "description": "订单备注（可选）" },
        "items": {
          "type": "array",
          "minItems": 1,
          "maxItems": 200,
          "description": "订单项列表（非空）",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["product_id", "quantity"],
            "properties": {
              "product_id": { "type": "integer", "minimum": 1 },
              "quantity":   { "type": "integer", "minimum": 1 }
            }
          }
        }
      }
    }',
    '订单创建入参 Schema（items 非空，每项必填 product_id / quantity）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);

-- 8. 订单更新入参（status 或 remark，二选一即可，但主键必填）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:update:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderUpdateParam",
      "additionalProperties": false,
      "required": ["id"],
      "properties": {
        "id":      { "type": "integer", "minimum": 1, "description": "订单主键（必填）" },
        "status":  { "type": "string", "enum": ["CREATED","PAID","SHIPPED","COMPLETED","CANCELLED"], "description": "订单目标状态（状态机合法转移由 dbExecute WHERE 校验）" },
        "remark":  { "type": "string", "maxLength": 512, "description": "订单备注更新" }
      }
    }',
    '订单更新入参 Schema（一般用于状态流转或修改备注）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);

-- 9. 订单单条查询入参
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:get:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderGetParam",
      "additionalProperties": false,
      "oneOf": [
        { "required": ["id"] },
        { "required": ["order_no"] }
      ],
      "properties": {
        "id":       { "type": "integer", "minimum": 1, "description": "订单主键" },
        "order_no": { "type": "string", "minLength": 1, "maxLength": 64, "description": "订单编号" }
      }
    }',
    '订单单条查询入参（id 与 order_no 二选一）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);

-- 10. 订单列表入参（user_id / status / 日期范围 + limit/offset）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:list:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderListParam",
      "additionalProperties": false,
      "properties": {
        "user_id":    { "type": "integer", "minimum": 1, "description": "用户过滤" },
        "status":     { "type": "string", "enum": ["CREATED","PAID","SHIPPED","COMPLETED","CANCELLED"], "description": "状态过滤" },
        "start_date": { "type": "string", "description": "下单起始日期（含），格式 yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss" },
        "end_date":   { "type": "string", "description": "下单截止日期（含）" },
        "limit":      { "type": "integer", "minimum": 1, "maximum": 500, "default": 20 },
        "offset":     { "type": "integer", "minimum": 0, "default": 0 }
      }
    }',
    '订单列表查询入参（支持用户、状态、日期范围）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);

-- 11. 订单分页入参（pageNo/pageSize）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:page:param', 'INPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderPageParam",
      "additionalProperties": false,
      "required": ["pageNo", "pageSize"],
      "properties": {
        "user_id":    { "type": "integer", "minimum": 1 },
        "status":     { "type": "string", "enum": ["CREATED","PAID","SHIPPED","COMPLETED","CANCELLED"] },
        "start_date": { "type": "string" },
        "end_date":   { "type": "string" },
        "pageNo":     { "type": "integer", "minimum": 1, "default": 1 },
        "pageSize":   { "type": "integer", "minimum": 1, "maximum": 200, "default": 20 }
      }
    }',
    '订单分页查询入参（必须提供 pageNo & pageSize）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);

-- 12. 订单出参（order_main 字段 + items[]）
INSERT IGNORE INTO `wf_schema`
    (schema_name, schema_type, schema_format, schema_json, description,
     scope, app_group, app_id, domain, frozen, created_at, updated_at)
VALUES (
    'order:result', 'OUTPUT', 'json-schema',
    '{
      "$schema": "http://json-schema.org/draft-07/schema#",
      "type": "object",
      "title": "OrderResult",
      "additionalProperties": true,
      "properties": {
        "id":           { "type": "integer" },
        "order_no":     { "type": "string" },
        "user_id":      { "type": "integer" },
        "total_amount": { "type": "number" },
        "pay_amount":   { "type": "number" },
        "status":       { "type": "string" },
        "remark":       { "type": "string" },
        "created_at":   { "type": ["string", "null"] },
        "updated_at":   { "type": ["string", "null"] },
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "id":           { "type": "integer" },
              "order_id":     { "type": "integer" },
              "product_id":   { "type": "integer" },
              "product_name": { "type": "string" },
              "price":        { "type": "number" },
              "quantity":     { "type": "integer" },
              "subtotal":     { "type": "number" }
            }
          }
        }
      }
    }',
    '订单出参 Schema（单条查询含 items[]；列表元素可只含主表）',
    'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key = 'order-worker' LIMIT 1),
    'db/order', 1, NOW(), NOW()
);
