-- ============================================================
-- V25：Seed 商品域 & 订单域工作流定义 + 函数集合 SET_REF + 多协议触发
--
-- 结构概览
-- ────────────────────────────────────────────────────────────
-- Part A : 4 个「函数集合」Workflow 定义（protocol=NULL，不对外直接触发）
--          product-crud / product-read / order-crud / order-read
-- Part B : 把 Part A 的 4 个发布成 wf_function（function_type=SET_REF）
--
-- 关键对齐：
--   wf_definition.app_id NOT NULL 用子查询 (SELECT id FROM app WHERE app_key=xxx)
--   target_groups 用 JSON 数组字符串 '["product-worker"]'
--   publish_target 用 '{"type":"GROUPS","groups":["xxx"]}'
--   status='ACTIVE' 保证 Runtime 拉到后立即可执行
--   dag_json / schema 全部使用 JSON 字符串字面量，避免 JSON_OBJECT 括号错位
-- ============================================================

SET NAMES utf8mb4;

-- =============================================================
-- Part A：4 个函数集合（可复用 DAG 单元，protocol=NULL，供 Part C/D/E 引用）
--   注意：函数集合本身的 input_schema / output_schema 必须非空，
--   否则 Part B 发布 SET_REF 时会被 FunctionSetService.publish()
--   的 require(schemas非空) 校验拦截。
-- =============================================================

-- 1. set:product-crud
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
    'Product CRUD Set (create helper)',
    '{"nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","required":["product_no","name","price"],"properties":{"product_no":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":256},"description":{"type":"string"},"category":{"type":"string"},"price":{"type":"number","minimum":0},"stock":{"type":"integer","minimum":0,"default":0},"status":{"type":"integer","enum":[0,1],"default":1}}}}},{"id":"insert_product","name":"insert product","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["validate"],"params":{"sql":"INSERT INTO product (product_no,name,description,category,price,stock,status) VALUES (:product_no,:name,:description,:category,:price,COALESCE(:stock,0),COALESCE(:status,1))","resultType":"UPDATE_COUNT","dataSource":"default","compensateSql":"DELETE FROM product WHERE product_no = :product_no"}},{"id":"load_back","name":"select last inserted","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":["insert_product"],"params":{"sql":"SELECT * FROM product WHERE product_no = :product_no","resultType":"SINGLE","dataSource":"default"}},{"id":"set_cache","name":"write product cache","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":2000,"dependsOn":["load_back"],"params":{"cmd":"SET","args":["product:${product_no}","${load_back}","EX","600"]}},{"id":"response","name":"wrap ok","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["set_cache"],"params":{}}]}',
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

-- 2. set:product-read
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
    'product-read',
    'Product Read Set (get + cache)',
    '{"nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","properties":{"id":{"type":"integer","minimum":1},"product_no":{"type":"string","maxLength":64}}}}},{"id":"build_key","name":"build cache key","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1000,"dependsOn":["validate"],"params":{"scriptBody":"var p = JSON.parse(input); (p.id ? \"product:id:\"+p.id : \"product:no:\"+p.product_no)"}},{"id":"get_cache","name":"redis GET","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1500,"dependsOn":["build_key"],"params":{"cmd":"GET","args":["${build_key}"]}},{"id":"db_fetch","name":"fallback DB","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":4000,"dependsOn":["get_cache"],"params":{"sql":"SELECT * FROM product WHERE (:id IS NOT NULL AND id = :id) OR (:product_no IS NOT NULL AND product_no = :product_no) LIMIT 1","resultType":"SINGLE","dataSource":"default"}},{"id":"cache_write","name":"refresh cache on miss","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1500,"dependsOn":["db_fetch"],"params":{"cmd":"SET","args":["${build_key}","${db_fetch}","EX","300"]}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["cache_write"],"params":{}}]}',
    '{"type":"object","properties":{"id":{"type":"integer"},"product_no":{"type":"string"}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":["object","null"]}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    NULL, NULL, NULL,
    '["product-worker"]',
    '{"type":"GROUPS","groups":["product-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    1, 'set:product-read', NULL, NULL,
    'system', NOW(), NOW()
);

-- 3. set:order-crud
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
    'order-crud',
    'Order Create CRUD Set (insert order + items in transaction)',
    '{"nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","required":["user_id","items"],"properties":{"user_id":{"type":"integer","minimum":1},"remark":{"type":"string"},"items":{"type":"array","minItems":1,"items":{"type":"object","required":["product_id","quantity"],"properties":{"product_id":{"type":"integer","minimum":1},"quantity":{"type":"integer","minimum":1}}}}}}},{"id":"gen_no","name":"generate order_no","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1000,"dependsOn":["validate"],"params":{"scriptBody":"var d=new Date(); \"ORD\"+d.getFullYear()+String(d.getMonth()+1).padStart(2,\"0\")+String(d.getDate()).padStart(2,\"0\")+Math.floor(Math.random()*1000000).toString().padStart(6,\"0\")"}},{"id":"calc_amounts","name":"compute total/pay amounts","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1500,"dependsOn":["gen_no"],"params":{"scriptBody":"var c=JSON.parse(input); var total=0; for(var i=0;i<c.items.length;i++){ total += (Number(c.items[i]._priceSnap||0)*c.items[i].quantity); } var pay=Math.max(0,total-(Number(c.discount||0))); JSON.stringify({total:total.toFixed(2), pay:pay.toFixed(2)});"}},{"id":"insert_main","name":"insert order_main","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":5000,"dependsOn":["calc_amounts"],"params":{"sql":"INSERT INTO order_main (order_no,user_id,total_amount,pay_amount,status,remark) VALUES (:gen_no,:user_id, :total, :pay, \"CREATED\", :remark)","resultType":"UPDATE_COUNT","dataSource":"default"}},{"id":"load_main","name":"select back order_main","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":2000,"dependsOn":["insert_main"],"params":{"sql":"SELECT * FROM order_main WHERE order_no = :gen_no","resultType":"SINGLE","dataSource":"default"}},{"id":"insert_items","name":"foreach insert order_item","type":"CUSTOM","functionRef":"builtin:foreach","timeoutMs":8000,"dependsOn":["load_main"],"params":{"collection":"${items}","itemAlias":"it","subWorkflow":{"nodes":[{"id":"one_item","name":"insert item","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":3000,"dependsOn":[],"params":{"sql":"INSERT INTO order_item (order_id,product_id,product_name,price,quantity,subtotal) VALUES (:${load_main.id}, :it.product_id, (SELECT name FROM product WHERE id=:it.product_id), (SELECT price FROM product WHERE id=:it.product_id), :it.quantity, (SELECT price FROM product WHERE id=:it.product_id)*:it.quantity)","resultType":"UPDATE_COUNT","dataSource":"default"}}]}}},{"id":"join_items","name":"join items list","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":2000,"dependsOn":["insert_items"],"params":{"sql":"SELECT * FROM order_item WHERE order_id = ${load_main.id}","resultType":"ROWS","dataSource":"default"}},{"id":"assemble","name":"merge main + items","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1500,"dependsOn":["join_items"],"params":{"scriptBody":"var m=JSON.parse(input[\"load_main\"]||\"{}\"); m.items=JSON.parse(input[\"join_items\"]||\"[]\"); JSON.stringify(m)"}},{"id":"del_user_cache","name":"evict user order list cache","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1500,"dependsOn":["assemble"],"params":{"cmd":"DEL","args":["order:user:${user_id}:list"]}},{"id":"response","name":"wrap ok","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["del_user_cache"],"params":{}}]}',
    '{"type":"object","required":["user_id","items"],"properties":{"user_id":{"type":"integer"},"remark":{"type":"string"},"items":{"type":"array"}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":"object"}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    NULL, NULL, NULL,
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'SAGA',
    1, 'set:order-crud',
    '["TransactionDecorator"]',
    NULL,
    'system', NOW(), NOW()
);

-- 4. set:order-read
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
    'order-read',
    'Order Read Set (get + items join + cache)',
    '{"nodes":[{"id":"validate","name":"paramValidate","type":"CUSTOM","functionRef":"builtin:paramValidate","timeoutMs":3000,"dependsOn":[],"params":{"schema":{"type":"object","properties":{"id":{"type":"integer","minimum":1},"order_no":{"type":"string","maxLength":64}}}}},{"id":"cache_get","name":"redis GET","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1500,"dependsOn":["validate"],"params":{"cmd":"GET","args":["order:${id != null ? id : \"no:\" + order_no}"]}},{"id":"query_main","name":"select order_main","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":4000,"dependsOn":["cache_get"],"params":{"sql":"SELECT * FROM order_main WHERE (:id IS NOT NULL AND id = :id) OR (:order_no IS NOT NULL AND order_no = :order_no) LIMIT 1","resultType":"SINGLE","dataSource":"default"}},{"id":"query_items","name":"select order_items","type":"CUSTOM","functionRef":"builtin:dbExecute","timeoutMs":4000,"dependsOn":["query_main"],"params":{"sql":"SELECT * FROM order_item WHERE order_id = ${query_main.id} ORDER BY id ASC","resultType":"ROWS","dataSource":"default"}},{"id":"merge","name":"assemble result","type":"CUSTOM","functionRef":"builtin:script_js","timeoutMs":1000,"dependsOn":["query_items"],"params":{"scriptBody":"var m=JSON.parse(input[\"query_main\"]||\"{}\"); m.items=JSON.parse(input[\"query_items\"]||\"[]\"); JSON.stringify(m)"}},{"id":"cache_set","name":"write cache 5min","type":"CUSTOM","functionRef":"builtin:redisCommand","timeoutMs":1500,"dependsOn":["merge"],"params":{"cmd":"SET","args":["order:${id != null ? id : \"no:\" + order_no}","${merge}","EX","300"]}},{"id":"response","name":"wrap","type":"CUSTOM","functionRef":"builtin:responseWrapper","timeoutMs":1000,"dependsOn":["cache_set"],"params":{}}]}',
    '{"type":"object","properties":{"id":{"type":"integer"},"order_no":{"type":"string"}}}',
    'json-schema',
    '{"type":"object","required":["code","message","data"],"properties":{"code":{"type":"integer"},"message":{"type":"string"},"data":{"type":["object","null"]}}}',
    'json-schema',
    'BUSINESS', 0, 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    NULL, NULL, NULL,
    '["order-worker"]',
    '{"type":"GROUPS","groups":["order-worker"]}',
    NULL,
    1, 'ACTIVE', 'NONE',
    1, 'set:order-read', NULL, NULL,
    'system', NOW(), NOW()
);

-- =============================================================
-- Part B：把 Part A 的 4 个发布成 wf_function（SET_REF 行）
--   config 字段格式对齐 FunctionSetService.buildConfig() 返回值
-- =============================================================

-- set:product-crud
REPLACE INTO `wf_function` (
    function_name, function_type, scope, app_group, app_id, source_ref, domain, config, status, created_at, updated_at
) VALUES (
    'set:product-crud', 'SET_REF', 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    NULL, 'FUNCTION_SET',
    CONCAT('{"setWorkflowId":"product-crud","definitionId":',
           (SELECT CAST(id AS CHAR) FROM wf_definition WHERE workflow_id='product-crud' LIMIT 1),
           ',"setRefName":"set:product-crud","category":"BUSINESS","_v":1,',
           '"inputSchema":{"type":"object","required":["product_no","name","price"]},',
           '"outputSchema":{"type":"object","required":["code","message","data"]}}'),
    'ACTIVE', NOW(), NOW()
);

-- set:product-read
REPLACE INTO `wf_function` (
    function_name, function_type, scope, app_group, app_id, source_ref, domain, config, status, created_at, updated_at
) VALUES (
    'set:product-read', 'SET_REF', 'PRIVATE', 'product-worker',
    (SELECT id FROM `app` WHERE app_key='product-worker' LIMIT 1),
    NULL, 'FUNCTION_SET',
    CONCAT('{"setWorkflowId":"product-read","definitionId":',
           (SELECT CAST(id AS CHAR) FROM wf_definition WHERE workflow_id='product-read' LIMIT 1),
           ',"setRefName":"set:product-read","category":"BUSINESS","_v":1,',
           '"inputSchema":{"type":"object"},',
           '"outputSchema":{"type":"object","required":["code","message","data"]}}'),
    'ACTIVE', NOW(), NOW()
);

-- set:order-crud
REPLACE INTO `wf_function` (
    function_name, function_type, scope, app_group, app_id, source_ref, domain, config, status, created_at, updated_at
) VALUES (
    'set:order-crud', 'SET_REF', 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    NULL, 'FUNCTION_SET',
    CONCAT('{"setWorkflowId":"order-crud","definitionId":',
           (SELECT CAST(id AS CHAR) FROM wf_definition WHERE workflow_id='order-crud' LIMIT 1),
           ',"setRefName":"set:order-crud","category":"BUSINESS","_v":1,',
           '"inputSchema":{"type":"object","required":["user_id","items"]},',
           '"outputSchema":{"type":"object","required":["code","message","data"]}}'),
    'ACTIVE', NOW(), NOW()
);

-- set:order-read
REPLACE INTO `wf_function` (
    function_name, function_type, scope, app_group, app_id, source_ref, domain, config, status, created_at, updated_at
) VALUES (
    'set:order-read', 'SET_REF', 'PRIVATE', 'order-worker',
    (SELECT id FROM `app` WHERE app_key='order-worker' LIMIT 1),
    NULL, 'FUNCTION_SET',
    CONCAT('{"setWorkflowId":"order-read","definitionId":',
           (SELECT CAST(id AS CHAR) FROM wf_definition WHERE workflow_id='order-read' LIMIT 1),
           ',"setRefName":"set:order-read","category":"BUSINESS","_v":1,',
           '"inputSchema":{"type":"object"},',
           '"outputSchema":{"type":"object","required":["code","message","data"]}}'),
    'ACTIVE', NOW(), NOW()
);
