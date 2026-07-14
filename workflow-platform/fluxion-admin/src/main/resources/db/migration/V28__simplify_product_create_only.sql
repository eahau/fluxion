-- ============================================================
-- V28：简化为仅保留【商品创建 HTTP】一个对外工作流
--      + 一个对应的可复用函数集合 set:product-crud
--
-- 执行动作：
--   Part A：旧的 13 个对外工作流（V26）+ 4 个函数集合（V25 Part A）
--           批量 status=DEPRECATED，避免 Designer 误展示 / Worker 加载
--           报错（特别是包含未正确转义双引号的 scriptBody）。
--   Part B：旧的 4 条 SET_REF wf_function 批量 status=INACTIVE。
--   Part C：重新插入 1 个函数集合 wf_definition（workflow_id=product-crud），
--           dag_json 明确 4 节点展开式（无 protocol，纯复用单元）。
--   Part D：发布 set:product-crud 对应的 wf_function 行
--           （function_type=SET_REF，config 含完整 input/outputSchema）。
--   Part E：重新插入对外 HTTP 工作流 product-create-http
--           （protocol=HTTP POST /api/v1/products，dag_json 4 节点展开式）
--
-- 关键约束（全部对齐 project_memory 与已有代码约定）：
--   ① dag_json / schema / config 全部使用 JSON 字符串字面量
--     （单引号包裹整个 JSON，禁用 JSON_OBJECT / JSON_ARRAY 函数拼接）
--   ② 如果节点 scriptBody 内含字面量双引号：
--     在 SQL 单引号字符串中写 \\\"  → 存储为 \"  → Jackson 正确反序列化
--     （V26 的教训：\" 在 MySQL 字面量里最终会变成裸 "，破坏 JSON 结构）
--   ③ builtin:dbExecute.resultType 必须使用大写枚举值：
--     UPDATE_COUNT | SINGLE | ROWS （禁止 count/one/list 等别名）
--   ④ builtin:redisCommand 参数必须是 {cmd, args:[...]}，
--     禁止使用 {command, key, value} 旧式字段
--   ⑤ MySQL JSON 字面量中 SQL 字符串里的双引号不需要额外起别名
--     （我们只用字符串，不用 JSON_OBJECT/JSON_ARRAY）
-- ============================================================

SET NAMES utf8mb4;

-- =============================================================
-- Part A：把 V25 Part A & V26 旧工作流全部置为 DEPRECATED
--         （V25 Part A 4 个函数集合 + V26 13 个对外工作流）
-- =============================================================
UPDATE `wf_definition`
   SET `status`       = 'DEPRECATED',
       `updated_at`   = NOW()
 WHERE `workflow_id` IN (
    'product-crud', 'product-read', 'order-crud', 'order-read',
    'product-create-http', 'product-get-http', 'product-update-http',
    'product-delete-http', 'product-page-http',
    'order-create-http', 'order-get-http', 'order-update-status-http',
    'order-cancel-http', 'order-page-http',
    'product-get-grpc', 'order-get-grpc', 'order-create-kafka'
 );

-- =============================================================
-- Part B：旧 SET_REF 函数集合全部置为 INACTIVE
-- =============================================================
UPDATE `wf_function`
   SET `status`     = 'INACTIVE',
       `updated_at` = NOW()
 WHERE `function_name` IN (
    'set:product-crud', 'set:product-read',
    'set:order-crud',   'set:order-read'
 );

-- =============================================================
-- Part C：重新插入函数集合 wf_definition（product-crud）
--          4 节点展开式：
--            ① validate   → builtin:paramValidate   （校验 product_no/name/price）
--            ② insert_p   → builtin:dbExecute      （INSERT INTO product UPDATE_COUNT）
--            ③ load_back  → builtin:dbExecute      （SELECT * FROM product WHERE product_no SINGLE）
--            ④ response   → builtin:responseWrapper（封装 code/message/data）
-- =============================================================
REPLACE INTO `wf_definition` (
    workflow_id, workflow_name, dag_json,
    input_schema, input_schema_format,
    output_schema, output_schema_format,
    category, is_protected, scope, app_group, app_id,
    protocol, method, bind_key,
    target_groups, publish_target, triggers_config,
    version, status, transaction_mode,
    is_published_set, set_ref_name, workflow_decorators, workflow_decorator_params,
    created_by, created_at, updated_at
) VALUES (
    'product-crud',
    'Product Create Set — 4 nodes expanded',
    -- 注意：JSON 字符串字面量，所有内嵌双引号无需额外 SQL 转义
    -- （因为我们不在这个函数集合里使用 scriptBody，纯 4 节点无内嵌引号场景）
    '{"nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":256},"description":{"type":"string"},"category":{"type":"string"},"price":{"type":"number","minimum":0},"stock":{"type":"integer","minimum":0,"default":0},"status":{"type":"integer","enum":[0,1],"default":1}}}}},{"id":"insert_p","name":"insert product","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["validate"],"params":{"sql":"INSERT INTO product (product_no,name,description,category,price,stock,status) VALUES (:product_no,:name,:description,:category,:price,COALESCE(:stock,0),COALESCE(:status,1))","resultType":"UPDATE_COUNT","dataSource":"default"}},{"id":"load_back","name":"select last inserted","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":["insert_p"],"params":{"sql":"SELECT * FROM product WHERE product_no = :product_no LIMIT 1","resultType":"SINGLE","dataSource":"default"}},{"id":"response","name":"wrap ok","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["load_back"],"params":{}}]}',
    '{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string"},"name":{"type":"string"},"price":{"type":"number"}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object"}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    NULL, NULL, NULL,
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    1, 'set:product-crud', NULL, NULL,
    'system', NOW(), NOW()
);

-- =============================================================
-- Part D：发布 SET_REF 行 set:product-crud
--          config 对齐 FunctionSetService.buildConfig() 输出结构
-- =============================================================
REPLACE INTO `wf_function` (
    function_name, function_type, scope, app_group, app_id, source_ref, domain, config, status, created_at, updated_at
) VALUES (
    'set:product-crud', 'SET_REF', 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    NULL, 'FUNCTION_SET',
    CONCAT('{"setWorkflowId":"product-crud","definitionId":',
           (SELECT CAST(id AS CHAR) FROM wf_definition WHERE workflow_id='product-crud' LIMIT 1),
           ',"setRefName":"set:product-crud","category":"BUSINESS","_v":1,',
           '"inputSchema":{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":256},"description":{"type":"string"},"category":{"type":"string"},"price":{"type":"number","minimum":0},"stock":{"type":"integer","minimum":0,"default":0},"status":{"type":"integer","enum":[0,1],"default":1}}},',
           '"outputSchema":{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object"}}}}'),
    'ACTIVE', NOW(), NOW()
);

-- =============================================================
-- Part E：重新插入对外 HTTP 工作流 product-create-http
--          protocol=HTTP, method=POST, bindKey=/api/v1/products
--          dag_json 4 节点展开式（和函数集合一致，避免单个 SET_REF 节点）
-- =============================================================
REPLACE INTO `wf_definition` (
    workflow_id, workflow_name, dag_json,
    input_schema, input_schema_format,
    output_schema, output_schema_format,
    category, is_protected, scope, app_group, app_id,
    protocol, method, bind_key,
    target_groups, publish_target, triggers_config,
    version, status, transaction_mode,
    is_published_set, set_ref_name, workflow_decorators, workflow_decorator_params,
    created_by, created_at, updated_at
) VALUES (
    'product-create-http',
    'Product Create HTTP (POST /api/v1/products) — expanded 4 nodes',
    -- 注意：这里直接展开 4 节点（不再用单个 CUSTOM 引用 set:product-crud）
    -- 确保 Designer 打开 /workflow/designer/product-create-http 时能看到完整链路
    '{"protocol":"HTTP","method":"POST","bindKey":"/api/v1/products","nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":256},"description":{"type":"string"},"category":{"type":"string"},"price":{"type":"number","minimum":0},"stock":{"type":"integer","minimum":0,"default":0},"status":{"type":"integer","enum":[0,1],"default":1}}}}},{"id":"insert_p","name":"insert product (dbExecute)","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["validate"],"params":{"sql":"INSERT INTO product (product_no,name,description,category,price,stock,status) VALUES (:product_no,:name,:description,:category,:price,COALESCE(:stock,0),COALESCE(:status,1))","resultType":"UPDATE_COUNT","dataSource":"default"}},{"id":"load_back","name":"select back inserted row","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":["insert_p"],"params":{"sql":"SELECT * FROM product WHERE product_no = :product_no LIMIT 1","resultType":"SINGLE","dataSource":"default"}},{"id":"response","name":"wrap response","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["load_back"],"params":{}}]}',
    '{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":256},"description":{"type":"string"},"category":{"type":"string"},"price":{"type":"number","minimum":0},"stock":{"type":"integer","minimum":0,"default":0},"status":{"type":"integer","enum":[0,1],"default":1}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object"}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'HTTP', 'POST', '/api/v1/products',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);
