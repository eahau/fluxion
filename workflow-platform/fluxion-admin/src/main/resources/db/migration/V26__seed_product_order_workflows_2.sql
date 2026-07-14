-- ============================================================
-- V26：Part C/D/E — 10 个 HTTP 协议工作流 + 2 gRPC + 1 Kafka
--   依赖 V25 中的函数集合（Part A/B）全部发布完成后执行。
--   dag_json / input_schema / triggers_config 全部使用 JSON 字符串字面量。
-- ============================================================

SET NAMES utf8mb4;

-- =============================================================
-- Part C：10 个 HTTP 协议工作流（5 商品域 + 5 订单域）
-- =============================================================

-- ────────────────────────────────────────────────────────────
-- 商品域 5 个（app_group=product-worker, target_groups=["product-worker"]）
-- ────────────────────────────────────────────────────────────

-- C1. POST /api/v1/products — 创建商品
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
    'Product Create HTTP (POST /api/v1/products)',
    '{"protocol":"HTTP","method":"POST","bindKey":"/api/v1/products","nodes":[{"id":"do_create","name":"delegate to set:product-crud","type":"CUSTOM","functionRef":"set:product-crud","timeoutMs":30000,"dependsOn":[],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:create:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:result' LIMIT 1),
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

-- C2. GET /api/v1/products/{id} — 单条查询
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
    'product-get-http',
    'Product Get HTTP (GET /api/v1/products/{id})',
    '{"protocol":"HTTP","method":"GET","bindKey":"/api/v1/products/{id}","nodes":[{"id":"cast_path","name":"cast {id} to long","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"do_get","name":"set:product-read","type":"CUSTOM","functionRef":"set:product-read","timeoutMs":15000,"dependsOn":["cast_path"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:get:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'HTTP', 'GET', '/api/v1/products/{id}',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C3. PUT /api/v1/products/{id} — 更新商品（Patch）
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
    'product-update-http',
    'Product Update HTTP (PUT /api/v1/products/{id})',
    '{"protocol":"HTTP","method":"PUT","bindKey":"/api/v1/products/{id}","nodes":[{"id":"validate","name":"paramValidate product:update:param","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schemaRef":"product:update:param"}},{"id":"cast","name":"cast id","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":["validate"],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"do_update","name":"dbExecute UPDATE","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["cast"],"params":{"sql":"UPDATE product SET product_no = COALESCE(:product_no, product_no), name = COALESCE(:name, name), description = COALESCE(:description, description), category = COALESCE(:category, category), price = COALESCE(:price, price), stock = COALESCE(:stock, stock), status = COALESCE(:status, status), updated_at = NOW() WHERE id = :id","resultType":"count","dataSource":"default","compensateSql":"UPDATE product SET updated_at = NOW() WHERE id = :id"}},{"id":"cache_del1","name":"DEL cache by id","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1000,"dependsOn":["do_update"],"params":{"command":"DEL","key":"product:id:${id}"}},{"id":"query_after","name":"select back after update","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":2000,"dependsOn":["cache_del1"],"params":{"sql":"SELECT * FROM product WHERE id = :id","resultType":"one","dataSource":"default"}},{"id":"cache_del2","name":"DEL cache by product_no","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1000,"dependsOn":["query_after"],"params":{"command":"DEL","key":"product:no:${JSON.parse(input[\"query_after\"]||\"{}\").product_no}"}},{"id":"response","name":"wrap ok","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["cache_del2"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:update:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'HTTP', 'PUT', '/api/v1/products/{id}',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C4. DELETE /api/v1/products/{id} — 软删除（status=0）
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
    'product-delete-http',
    'Product Soft-Delete HTTP (DELETE /api/v1/products/{id})',
    '{"protocol":"HTTP","method":"DELETE","bindKey":"/api/v1/products/{id}","nodes":[{"id":"cast","name":"cast id","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"validate","name":"paramValidate {id required}","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":1000,"dependsOn":["cast"],"params":{"schema":{"type":"object","required":["id"],"properties":{"id":{"type":"integer","minimum":1}}}}},{"id":"get_before","name":"select before delete to capture product_no","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":2000,"dependsOn":["validate"],"params":{"sql":"SELECT id, product_no FROM product WHERE id = :id","resultType":"one","dataSource":"default"}},{"id":"do_delete","name":"UPDATE status=0 (soft delete)","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["get_before"],"params":{"sql":"UPDATE product SET status = 0, updated_at = NOW() WHERE id = :id AND status = 1","resultType":"count","dataSource":"default"}},{"id":"response","name":"wrap ok","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["do_delete"],"params":{}}]}',
    '{"type":"object","required":["id"],"properties":{"id":{"type":"integer","minimum":1}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"integer"}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'HTTP', 'DELETE', '/api/v1/products/{id}',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C5. GET /api/v1/products — 商品分页列表
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
    'product-page-http',
    'Product Page List HTTP (GET /api/v1/products)',
    '{"protocol":"HTTP","method":"GET","bindKey":"/api/v1/products","nodes":[{"id":"validate","name":"paramValidate product:page:param","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schemaRef":"product:page:param"}},{"id":"count","name":"count total","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":["validate"],"params":{"sql":"SELECT COUNT(*) c FROM product WHERE 1=1 AND (:category IS NULL OR category = :category) AND (:status IS NULL OR status = :status) AND (:keyword IS NULL OR name LIKE CONCAT(\"%\", :keyword, \"%\") OR product_no LIKE CONCAT(\"%\", :keyword, \"%\"))","resultType":"one","dataSource":"default"}},{"id":"list","name":"select page items","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["count"],"params":{"sql":"SELECT * FROM product WHERE 1=1 AND (:category IS NULL OR category = :category) AND (:status IS NULL OR status = :status) AND (:keyword IS NULL OR name LIKE CONCAT(\"%\", :keyword, \"%\") OR product_no LIKE CONCAT(\"%\", :keyword, \"%\")) ORDER BY id DESC LIMIT :pageSize OFFSET (:pageNo - 1) * :pageSize","resultType":"list","limit":1000,"dataSource":"default"}},{"id":"paginate","name":"envelope items + count","type":"CUSTOM","functionRef":"builtin:paginate","timeoutMs":500,"dependsOn":["list"],"params":{"page":"${pageNo}","size":"${pageSize}","total":"${JSON.parse(input[\"count\"]||\"{}\").c}"}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["paginate"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:page:param' LIMIT 1),
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object","required":["items","page","size","total"]}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'HTTP', 'GET', '/api/v1/products',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- ────────────────────────────────────────────────────────────
-- 订单域 5 个（app_group=order-worker）
-- ────────────────────────────────────────────────────────────

-- C6. POST /api/v1/orders — 创建订单（直接调用 set:order-crud）
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
    'order-create-http',
    'Order Create HTTP (POST /api/v1/orders)',
    '{"protocol":"HTTP","method":"POST","bindKey":"/api/v1/orders","nodes":[{"id":"do_create","name":"set:order-crud","type":"CUSTOM","functionRef":"set:order-crud","timeoutMs":60000,"dependsOn":[],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:create:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'HTTP', 'POST', '/api/v1/orders',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C7. GET /api/v1/orders/{id} — 单条查询（直接调用 set:order-read）
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
    'order-get-http',
    'Order Get HTTP (GET /api/v1/orders/{id})',
    '{"protocol":"HTTP","method":"GET","bindKey":"/api/v1/orders/{id}","nodes":[{"id":"cast","name":"cast {id}","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"do_get","name":"set:order-read","type":"CUSTOM","functionRef":"set:order-read","timeoutMs":15000,"dependsOn":["cast"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:get:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'HTTP', 'GET', '/api/v1/orders/{id}',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C8. PUT /api/v1/orders/{id}/status — 状态流转（SQL WHERE 状态机校验）
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
    'order-update-status-http',
    'Order Status Update HTTP (PUT /api/v1/orders/{id}/status)',
    '{"protocol":"HTTP","method":"PUT","bindKey":"/api/v1/orders/{id}/status","nodes":[{"id":"merge","name":"merge path id and body status","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"validate","name":"paramValidate order:update:param","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":2000,"dependsOn":["merge"],"params":{"schemaRef":"order:update:param"}},{"id":"enforce_required","name":"require status or remark","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":["validate"],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); if(!c.status && !c.remark) throw new Error(\"At least one of status, remark must be provided\"); JSON.stringify(c)"}},{"id":"update_main","name":"UPDATE order_main (state machine in WHERE)","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["enforce_required"],"params":{"sql":"UPDATE order_main SET status = COALESCE(:status, status), remark = COALESCE(:remark, remark), updated_at = NOW() WHERE id = :id AND (:status IS NULL OR (status=\"CREATED\" AND :status IN (\"PAID\",\"CANCELLED\")) OR (status=\"PAID\" AND :status IN (\"SHIPPED\",\"CANCELLED\")) OR (status=\"SHIPPED\" AND :status = \"COMPLETED\"))","resultType":"count","dataSource":"default"}},{"id":"check_affected","name":"fail on 0 affected rows","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":["update_main"],"params":{"scriptBody":"var n=Number(JSON.parse(input||\"0\")); if(n===0) throw new Error(\"Order status transition not allowed / Order not found\"); n;"}},{"id":"cache_invalidate","name":"invalidate cache","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1000,"dependsOn":["check_affected"],"params":{"command":"DEL","key":"order:${id}"}},{"id":"load","name":"reload order","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":2000,"dependsOn":["cache_invalidate"],"params":{"sql":"SELECT * FROM order_main WHERE id = :id","resultType":"one","dataSource":"default"}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["load"],"params":{}}]}',
    '{"type":"object","required":["id","status"],"properties":{"id":{"type":"integer","minimum":1},"status":{"type":"string","enum":["CREATED","PAID","SHIPPED","COMPLETED","CANCELLED"]},"remark":{"type":"string"}}}',
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'HTTP', 'PUT', '/api/v1/orders/{id}/status',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C9. DELETE /api/v1/orders/{id} — 取消订单（CREATED/PAID → CANCELLED）
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
    'order-cancel-http',
    'Order Cancel HTTP (DELETE /api/v1/orders/{id})',
    '{"protocol":"HTTP","method":"DELETE","bindKey":"/api/v1/orders/{id}","nodes":[{"id":"cast","name":"cast id","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); c.id=Number(c.id); JSON.stringify(c)"}},{"id":"cancel","name":"UPDATE status=CANCELLED (WHERE IN CREATED,PAID)","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["cast"],"params":{"sql":"UPDATE order_main SET status=\"CANCELLED\", updated_at=NOW() WHERE id = :id AND status IN (\"CREATED\",\"PAID\")","resultType":"count","dataSource":"default"}},{"id":"check","name":"verify affected","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":["cancel"],"params":{"scriptBody":"var n=Number(JSON.parse(input||\"0\")); if(n===0) throw new Error(\"Order not found or status not cancellable\"); n;"}},{"id":"cache_invalidate","name":"DEL cache","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1000,"dependsOn":["check"],"params":{"command":"DEL","key":"order:${id}"}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["cache_invalidate"],"params":{}}]}',
    '{"type":"object","required":["id"],"properties":{"id":{"type":"integer","minimum":1}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"integer"}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'HTTP', 'DELETE', '/api/v1/orders/{id}',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- C10. GET /api/v1/orders — 订单分页列表（按 user_id/status/日期 过滤）
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
    'order-page-http',
    'Order Page List HTTP (GET /api/v1/orders)',
    '{"protocol":"HTTP","method":"GET","bindKey":"/api/v1/orders","nodes":[{"id":"validate","name":"paramValidate order:page:param","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schemaRef":"order:page:param"}},{"id":"count","name":"SELECT COUNT(*)","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":["validate"],"params":{"sql":"SELECT COUNT(*) c FROM order_main WHERE 1=1 AND (:user_id IS NULL OR user_id = :user_id) AND (:status IS NULL OR status = :status) AND (:start_date IS NULL OR created_at >= CAST(:start_date AS DATETIME)) AND (:end_date IS NULL OR created_at <= CAST(:end_date AS DATETIME))","resultType":"one","dataSource":"default"}},{"id":"list","name":"SELECT page items","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["count"],"params":{"sql":"SELECT * FROM order_main WHERE 1=1 AND (:user_id IS NULL OR user_id = :user_id) AND (:status IS NULL OR status = :status) AND (:start_date IS NULL OR created_at >= CAST(:start_date AS DATETIME)) AND (:end_date IS NULL OR created_at <= CAST(:end_date AS DATETIME)) ORDER BY id DESC LIMIT :pageSize OFFSET (:pageNo - 1) * :pageSize","resultType":"list","limit":1000,"dataSource":"default"}},{"id":"paginate","name":"envelope","type":"CUSTOM","functionRef":"builtin:paginate","timeoutMs":500,"dependsOn":["list"],"params":{"page":"${pageNo}","size":"${pageSize}","total":"${JSON.parse(input[\"count\"]||\"{}\").c}"}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["paginate"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:page:param' LIMIT 1),
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object","required":["items","page","size","total"]}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'HTTP', 'GET', '/api/v1/orders',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- =============================================================
-- Part D：2 个 gRPC 协议工作流（ProductService/GetProduct, OrderService/GetOrder）
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
    'product-get-grpc',
    'Product Get gRPC (ProductService/GetProduct)',
    '{"protocol":"GRPC","method":"UNARY","bindKey":"ProductService/GetProduct","nodes":[{"id":"cast","name":"cast id or product_no","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); if(c.id) c.id=Number(c.id); JSON.stringify(c)"}},{"id":"call_set","name":"set:product-read","type":"CUSTOM","functionRef":"set:product-read","timeoutMs":15000,"dependsOn":["cast"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:get:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='product:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    'GRPC', 'UNARY', 'ProductService/GetProduct',
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    '[{"type":"GRPC","serviceName":"ProductService","methodName":"GetProduct"}]',
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

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
    'order-get-grpc',
    'Order Get gRPC (OrderService/GetOrder)',
    '{"protocol":"GRPC","method":"UNARY","bindKey":"OrderService/GetOrder","nodes":[{"id":"cast","name":"cast id or order_no","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":[],"params":{"scriptBody":"var c=JSON.parse(input||\"{}\"); if(c.id) c.id=Number(c.id); JSON.stringify(c)"}},{"id":"call_set","name":"set:order-read","type":"CUSTOM","functionRef":"set:order-read","timeoutMs":15000,"dependsOn":["cast"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:get:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'GRPC', 'UNARY', 'OrderService/GetOrder',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    '[{"type":"GRPC","serviceName":"OrderService","methodName":"GetOrder"}]',
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);

-- =============================================================
-- Part E：1 个 Kafka 协议工作流 — 消费 topic: workflow.order.created
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
    'order-create-kafka',
    'Order Create Kafka Consumer (topic=workflow.order.created)',
    '{"protocol":"KAFKA","method":"CONSUME","bindKey":"workflow.order.created","nodes":[{"id":"parse","name":"deserialize kafka message body","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1000,"dependsOn":[],"params":{"scriptBody":"var msg=JSON.parse(input||\"{}\"); var payload = msg.payload || msg.value || msg; typeof payload === \"string\" ? payload : JSON.stringify(payload)"}},{"id":"retry_check","name":"dead-letter guard by header","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":500,"dependsOn":["parse"],"params":{"scriptBody":"var m=JSON.parse(input||\"{}\"); var h = m.headers || {}; var retry = Number(h.retry_count||0); if(retry > 3) throw new Error(\"DLQ: max retries exceeded\"); JSON.stringify(m.body||m)"}},{"id":"create","name":"delegate set:order-crud","type":"CUSTOM","functionRef":"set:order-crud","timeoutMs":60000,"dependsOn":["retry_check"],"params":{}}]}',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:create:param' LIMIT 1),
    'json-schema',
    (SELECT schema_json FROM wf_schema WHERE schema_name='order:result' LIMIT 1),
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    'KAFKA', 'CONSUME', 'workflow.order.created',
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    '[{"type":"KAFKA","topic":"workflow.order.created","groupId":"order-worker-create-consumer","deadLetterTopic":"workflow.order.created.DLT","retryPolicy":{"maxAttempts":3,"backoffMs":2000}}]',
    1, 'ACTIVE', 'NONE',
    0, NULL, NULL, NULL,
    'system', NOW(), NOW()
);
