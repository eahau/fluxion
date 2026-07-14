-- ============================================================
--  test-worker HTTP Inbound 全链路测试工作流种子
--
--  工作流执行链路（HTTP Inbound 标准三段式）：
--    1. builtin:paramValidate -- 按 input-schema 校验请求入参
--    2. builtin:dbExecute / builtin:redisCommand -- 执行业务查询
--    3. builtin:responseWrapper -- 包装 {code, message, data} 返回
--
--  节点通过 dependsOn 串行执行：
--    validate -> query -> response
--  确保上游输出作为下游 directInput 传递。
-- ============================================================


-- ═══════════════════════════════════════════════════════════
--  1. DB 查询测试工作流  test-worker-db-query
--     HTTP: POST http://<worker>:8082/api/test/db-query
--     Body: { "userId": 1001 }
--     Resp: { "code":200, "message":"success", "data":{"id":1001, "name":"..."} }
-- ═══════════════════════════════════════════════════════════
REPLACE INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`,
    `input_schema`, `input_schema_format`,
    `output_schema`, `output_schema_format`,
    `category`, `protocol`, `method`, `bind_key`,
    `scope`, `app_group`, `target_groups`, `publish_target`,
    `version`, `status`, `transaction_mode`, `is_protected`,
    `created_at`, `updated_at`
) VALUES (
    'test-worker-db-query',
    'Test Worker DB Query (paramValidate -> dbExecute -> responseWrapper)',
    -- ── DAG：串行 3 节点 ──
    JSON_OBJECT(
        'protocol', 'HTTP',
        'method',   'POST',
        'bindKey',  '/api/test/db-query',
        'nodes', JSON_ARRAY(
            -- 节点 1：入参校验（输出校验通过的原始入参）
            JSON_OBJECT(
                'id',          'validate',
                'name',        'paramValidate',
                'type',        'CUSTOM',
                'functionRef', 'builtin:paramValidate',
                'timeoutMs',   3000,
                'dependsOn',   JSON_ARRAY(),
                'params', JSON_OBJECT(
                    'schema', JSON_OBJECT(
                        'type',     'object',
                        'required', JSON_ARRAY('userId'),
                        'properties', JSON_OBJECT(
                            'userId', JSON_OBJECT('type', 'integer', 'description', 'user id, primary key')
                        )
                    )
                )
            ),
            -- 节点 2：DB 查询（WHERE id = :userId，从 validate 输出里拿）
            JSON_OBJECT(
                'id',          'query_user',
                'name',        'query user by id',
                'type',        'CUSTOM',
                'functionRef', 'builtin:dbExecute',
                'timeoutMs',   5000,
                'dependsOn',   JSON_ARRAY('validate'),
                'params', JSON_OBJECT(
                    'sql',        'SELECT id, name, updated_at FROM test_user_profile WHERE id = :userId',
                    'resultType', 'one',
                    'dataSource', 'default'
                )
            ),
            -- 节点 3：包装统一响应体
            JSON_OBJECT(
                'id',          'response',
                'name',        'wrap response',
                'type',        'CUSTOM',
                'functionRef', 'builtin:responseWrapper',
                'timeoutMs',   1000,
                'dependsOn',   JSON_ARRAY('query_user'),
                'params', JSON_OBJECT()
            )
        )
    ),
    -- input schema
    JSON_OBJECT(
        'type',       'object',
        'required',   JSON_ARRAY('userId'),
        'properties', JSON_OBJECT(
            'userId',   JSON_OBJECT('type', 'integer', 'description', 'user id')
        )
    ),
    'json-schema',
    -- output schema
    JSON_OBJECT(
        'type',       'object',
        'required',   JSON_ARRAY('code', 'message', 'data'),
        'properties', JSON_OBJECT(
            'code',    JSON_OBJECT('type', 'integer'),
            'message', JSON_OBJECT('type', 'string'),
            'data',    JSON_OBJECT(
                'type',       'object',
                'properties', JSON_OBJECT(
                    'id',         JSON_OBJECT('type', 'integer'),
                    'name',       JSON_OBJECT('type', 'string'),
                    'updated_at', JSON_OBJECT('type', 'string')
                )
            )
        )
    ),
    'json-schema',
    -- bind
    'BUSINESS', 'HTTP', 'POST', '/api/test/db-query',
    -- scope + group
    'PRIVATE', 'test-worker',
    '["test-worker"]',
    '{"type":"GROUPS","groups":["test-worker"]}',
    1, 'ACTIVE', 'NONE', 0,
    NOW(), NOW()
);


-- ═══════════════════════════════════════════════════════════
--  2. Redis 读写测试工作流  test-worker-redis-rw
--     HTTP: POST http://<worker>:8082/api/test/redis-rw
--     Body: { "cacheKey": "test:u:1", "cacheValue": "hello" }
--     Resp: { "code":200, "message":"success", "data":{set, get, del} }
-- ═══════════════════════════════════════════════════════════
REPLACE INTO `wf_definition` (
    `workflow_id`, `workflow_name`, `dag_json`,
    `input_schema`, `input_schema_format`,
    `output_schema`, `output_schema_format`,
    `category`, `protocol`, `method`, `bind_key`,
    `scope`, `app_group`, `target_groups`, `publish_target`,
    `version`, `status`, `transaction_mode`, `is_protected`,
    `created_at`, `updated_at`
) VALUES (
    'test-worker-redis-rw',
    'Test Worker Redis R/W (paramValidate -> redisCmd -> responseWrapper)',
    JSON_OBJECT(
        'protocol', 'HTTP',
        'method',   'POST',
        'bindKey',  '/api/test/redis-rw',
        'nodes', JSON_ARRAY(
            -- 1. 入参校验
            JSON_OBJECT(
                'id',          'validate',
                'name',        'paramValidate',
                'type',        'CUSTOM',
                'functionRef', 'builtin:paramValidate',
                'timeoutMs',   3000,
                'dependsOn',   JSON_ARRAY(),
                'params', JSON_OBJECT(
                    'schema', JSON_OBJECT(
                        'type',     'object',
                        'required', JSON_ARRAY('cacheKey', 'cacheValue'),
                        'properties', JSON_OBJECT(
                            'cacheKey',   JSON_OBJECT('type', 'string', 'minLength', 1),
                            'cacheValue', JSON_OBJECT('type', 'string')
                        )
                    )
                )
            ),
            -- 2a. SET（TTL 5 min）
            JSON_OBJECT(
                'id',          'set_key',
                'name',        'SET cacheKey cacheValue EX 300',
                'type',        'CUSTOM',
                'functionRef', 'builtin:redisCommand',
                'timeoutMs',   3000,
                'dependsOn',   JSON_ARRAY('validate'),
                'params', JSON_OBJECT(
                    'command', 'SET',
                    'key',     '${cacheKey}',
                    'args',    JSON_ARRAY('${cacheValue}', 'EX', '300')
                )
            ),
            -- 2b. GET（依赖 SET 完成，确保拿到写入值）
            JSON_OBJECT(
                'id',          'get_key',
                'name',        'GET cacheKey',
                'type',        'CUSTOM',
                'functionRef', 'builtin:redisCommand',
                'timeoutMs',   3000,
                'dependsOn',   JSON_ARRAY('set_key'),
                'params', JSON_OBJECT(
                    'command', 'GET',
                    'key',     '${cacheKey}'
                )
            ),
            -- 2c. DEL（清理）
            JSON_OBJECT(
                'id',          'del_key',
                'name',        'DEL cacheKey',
                'type',        'CUSTOM',
                'functionRef', 'builtin:redisCommand',
                'timeoutMs',   3000,
                'dependsOn',   JSON_ARRAY('get_key'),
                'params', JSON_OBJECT(
                    'command', 'DEL',
                    'key',     '${cacheKey}'
                )
            ),
            -- 3. 包装响应（DEL 输出作为 data 主体；链路 validate→SET→GET→DEL→response）
            JSON_OBJECT(
                'id',          'response',
                'name',        'wrap response',
                'type',        'CUSTOM',
                'functionRef', 'builtin:responseWrapper',
                'timeoutMs',   1000,
                'dependsOn',   JSON_ARRAY('del_key'),
                'params', JSON_OBJECT()
            )
        )
    ),
    -- input schema
    JSON_OBJECT(
        'type',       'object',
        'required',   JSON_ARRAY('cacheKey', 'cacheValue'),
        'properties', JSON_OBJECT(
            'cacheKey',   JSON_OBJECT('type', 'string', 'description', 'redis key'),
            'cacheValue', JSON_OBJECT('type', 'string', 'description', 'redis value')
        )
    ),
    'json-schema',
    -- output schema
    JSON_OBJECT(
        'type',       'object',
        'required',   JSON_ARRAY('code', 'message', 'data'),
        'properties', JSON_OBJECT(
            'code',    JSON_OBJECT('type', 'integer'),
            'message', JSON_OBJECT('type', 'string'),
            'data',    JSON_OBJECT('type', 'integer', 'description', 'DEL removed key count (usually 1)')
        )
    ),
    'json-schema',
    -- bind
    'BUSINESS', 'HTTP', 'POST', '/api/test/redis-rw',
    -- scope + group
    'PRIVATE', 'test-worker',
    '["test-worker"]',
    '{"type":"GROUPS","groups":["test-worker"]}',
    1, 'ACTIVE', 'NONE', 0,
    NOW(), NOW()
);


-- ═══════════════════════════════════════════════════════════
--  附录：预置数据（DB 工作流查询的目标表 + 一条样例行）
-- ═══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS test_user_profile (
    id         BIGINT       PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO test_user_profile (id, name) VALUES
    (1001, 'alice'),
    (1002, 'bob'),
    (1003, 'charlie')
ON DUPLICATE KEY UPDATE name = VALUES(name);


-- ═══════════════════════════════════════════════════════════
--  启动 test-worker：
--    cd d:\Projects\workflow\workflow\workflow-platform
--    .\gradlew.bat :fluxion-runtime:bootRun `
--        --args="--spring.profiles.active=local `
--               --server.port=8082 `
--               --spring.application.name=test-worker `
--               --workflow.instance.app-group=test-worker"
--
--  调用示例（PowerShell）：
--    # DB 查询
--    Invoke-RestMethod -Method POST http://localhost:8082/api/test/db-query `
--      -ContentType 'application/json' -Body '{"userId":1001}'
--
--    # Redis R/W（需要本地 Redis 运行并移除 Redis autoconfigure exclude）
--    Invoke-RestMethod -Method POST http://localhost:8082/api/test/redis-rw `
--      -ContentType 'application/json' -Body '{"cacheKey":"test:u:1","cacheValue":"hello"}'
-- ============================================================
