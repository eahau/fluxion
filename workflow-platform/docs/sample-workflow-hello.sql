-- 最简示例工作流：直接写入 wf_definition 表
-- 工作流 ID: demo-hello-world
-- 访问方式: GET /api/demo/hello（发布后生效）

INSERT IGNORE INTO `wf_definition` (
    `workflow_id`,
    `workflow_name`,
    `dag_json`,
    `input_schema`,
    `output_schema`,
    `protocol`,
    `method`,
    `bind_key`,
    `category`,
    `version`,
    `status`,
    `transaction_mode`,
    `is_protected`,
    `created_at`,
    `updated_at`
) VALUES (
    'demo-hello-world',
    'Hello World 示例工作流',
    '{"nodes":[{"id":"fetch","name":"获取示例数据","type":"CUSTOM","functionRef":"builtin:httpCall","timeoutMs":10000,"params":{"url":"https://httpbin.org/get","method":"GET","timeout":5000},"dependsOn":[]}]}',
    '{"type":"object","properties":{"name":{"type":"string","description":"问候名称"}}}',
    '{"type":"object"}',
    'HTTP',
    'GET',
    '/api/demo/hello',
    'BUSINESS',
    1,
    'DRAFT',
    'NONE',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
