-- ============================================================
-- V9：初始化内置函数（builtin:*）的入参/出参 Schema 到 wf_schema 表
--   作为唯一权威数据源（Single Source of Truth），代码侧 BuiltinFunctionMetas
--   仅通过引用名（builtin:<fn>:param / builtin:<fn>:output）指向此处，
--   不再 hardcode JSON。运行时由 SchemaManager 按引用名解析校验。
--   使用 INSERT IGNORE：启动重复执行不会报错，也不会覆盖已有（用户可能已修改）的 Schema。
-- ============================================================

INSERT IGNORE INTO `wf_schema`
    (`schema_name`, `schema_type`, `schema_json`, `description`, `schema_format`, `frozen`, `scope`)
VALUES

-- 1. builtin:cacheGet 入参
('builtin:cacheGet:param', 'INPUT',
 '{"type":"object","required":["key"],"properties":{"key":{"type":"string","description":"缓存键，支持 {paramName} 模板"}}}',
 '内置函数 builtin:cacheGet 入参', 'json-schema', 1, 'PLATFORM'),

-- 2. builtin:cacheSet 入参
('builtin:cacheSet:param', 'INPUT',
 '{"type":"object","required":["key"],"properties":{"key":{"type":"string","description":"缓存键，支持 {paramName} 模板"},"ttlSeconds":{"type":"integer","default":3600}}}',
 '内置函数 builtin:cacheSet 入参', 'json-schema', 1, 'PLATFORM'),

-- 3. builtin:conditionBranch 入参
('builtin:conditionBranch:param', 'INPUT',
 '{"type":"object","required":["conditions"],"properties":{"conditions":{"type":"array","description":"条件列表，按顺序匹配，首个命中的条件决定跳转目标","items":{"type":"object","properties":{"logic":{"type":"string","description":"规则组合逻辑：and（全部满足）或 or（任一满足），默认 and","enum":["and","or"],"default":"and"},"rules":{"type":"array","description":"规则列表，1 条规则为单条件，多条规则按 logic 组合","items":{"type":"object","required":["field","operator"],"properties":{"field":{"type":"string","description":"字段名，支持嵌套路径如 user.age、items"},"operator":{"type":"string","description":"操作符（否定语义通过 negate=true 实现，如 eq+negate = 不等于）","enum":["eq","gt","gte","lt","lte","in","contains","isNull","startsWith","endsWith","regex"]},"value":{"type":"string","description":"比较目标值，in 操作符支持逗号分隔；isNull 操作符无需填写"},"negate":{"type":"boolean","description":"是否对当前规则结果取反","default":false}}}},"negate":{"type":"boolean","description":"是否对当前条件整体结果取反","default":false},"condition":{"type":"string","description":"JEXL 表达式（高级选项，rules 为空时生效）"},"target":{"type":"string","description":"条件匹配时跳转的目标节点 ID"}}}},"defaultTarget":{"type":"string","description":"所有条件均未匹配时跳转的默认目标节点 ID"}}}',
 '内置函数 builtin:conditionBranch 入参', 'json-schema', 1, 'PLATFORM'),

-- 4. builtin:filter 入参
('builtin:filter:param', 'INPUT',
 '{"type":"object","properties":{"logic":{"type":"string","description":"规则组合逻辑：and（全部满足）或 or（任一满足），默认 and","enum":["and","or"],"default":"and"},"rules":{"type":"array","description":"规则列表，1 条规则为单条件，多条规则按 logic 组合","items":{"type":"object","required":["field","operator"],"properties":{"field":{"type":"string","description":"字段名，支持嵌套路径如 user.age、items"},"operator":{"type":"string","description":"操作符（否定语义通过 negate=true 实现，如 eq+negate = 不等于）","enum":["eq","gt","gte","lt","lte","in","contains","isNull","startsWith","endsWith","regex"]},"value":{"type":"string","description":"比较目标值，in 操作符支持逗号分隔；isNull 操作符无需填写"},"negate":{"type":"boolean","description":"是否对当前规则结果取反","default":false}}}},"negate":{"type":"boolean","description":"是否对整体条件结果取反","default":false},"condition":{"type":"string","description":"JEXL 表达式（高级选项，rules 为空时生效）"}}}',
 '内置函数 builtin:filter 入参', 'json-schema', 1, 'PLATFORM'),

-- 5. builtin:dbExecute 入参
('builtin:dbExecute:param', 'INPUT',
 '{"type":"object","required":["sql"],"properties":{"sql":{"type":"string","description":"SQL 语句，支持 :paramName 命名参数和 ${template} 表名模板","x-widget":"dbSqlEditor","x-i18n":{"zh":{"description":"SQL 语句，支持 :paramName 命名参数和 ${template} 表名模板，可执行 SELECT/INSERT/UPDATE/DELETE/DDL 等任意 SQL"},"en":{"description":"SQL statement, supports :paramName named parameters and ${template} table name templates for SELECT/INSERT/UPDATE/DELETE/DDL"}}},"params":{"type":"object","description":"SQL 参数绑定：键为 :paramName 参数名，值为字面量或 {ref} 引用上游节点输出","additionalProperties":true,"x-i18n":{"zh":{"description":"SQL 参数绑定：键为 :paramName 参数名，值为字面量或 {ref} 引用上游节点输出字段"},"en":{"description":"SQL parameter bindings: key is :paramName, value is literal or {ref} referencing upstream node output"}}},"dataSource":{"type":"string","default":"default","description":"数据源名称","x-i18n":{"zh":{"description":"数据源名称，多数据源场景使用，如 default / order / user"},"en":{"description":"Data source name, used in multi-datasource scenarios, e.g. default / order / user"}}},"resultType":{"type":"string","enum":["list","one","count"],"default":"list","description":"查询结果类型（仅 SELECT/WITH 生效）","x-i18n":{"zh":{"description":"查询结果类型（仅 SELECT/WITH 生效）：list 返回列表、one 返回单条、count 返回行数"},"en":{"description":"Query result type (SELECT/WITH only): list returns array, one returns single row, count returns row count"}}},"limit":{"type":"integer","default":1000,"description":"查询最大返回行数，默认 1000；<=0 表示不限","x-i18n":{"zh":{"description":"查询最大返回行数，默认 1000；<=0 表示不限"},"en":{"description":"Max rows to return for queries, default 1000; <=0 means unlimited"}}},"compensateSql":{"type":"string","description":"Saga 补偿 SQL（写操作逆操作）","x-widget":"dbSqlEditor","x-i18n":{"zh":{"description":"Saga 补偿 SQL（写操作失败时执行的逆操作 SQL）"},"en":{"description":"Saga compensation SQL (reverse operation SQL executed on write failure)"}}},"compensateFunctionRef":{"type":"string","description":"Saga 补偿函数引用","x-i18n":{"zh":{"description":"Saga 补偿函数引用（与 compensateSql 二选一）"},"en":{"description":"Saga compensation function reference (mutually exclusive with compensateSql)"}}}}',
 '内置函数 builtin:dbExecute 入参', 'json-schema', 1, 'PLATFORM'),

-- 6. builtin:dbExecute 出参
('builtin:dbExecute:output', 'OUTPUT',
 '{"oneOf":[{"type":"array","items":{},"description":"resultType=list 查询返回 [{col:val}, ...]"},{"type":"object","description":"resultType=one 返回单条记录 {col:val} 或写操作的响应对象"},{"type":"integer","description":"resultType=count 返回行数（count(*)）或写操作影响的行数"},{"type":"number"},{"type":"null"}],"description":"查询返回 list/map/count，写操作返回生成键或影响行数"}',
 '内置函数 builtin:dbExecute 出参', 'json-schema', 1, 'PLATFORM'),

-- 7. builtin:errorWrapper 入参
('builtin:errorWrapper:param', 'INPUT',
 '{"type":"object","properties":{"code":{"type":"integer","default":500},"message":{"type":"string","description":"错误消息（未配置时尝试从输入提取）"},"data":{"description":"错误附加数据（未配置时使用上游节点输出）"}}}',
 '内置函数 builtin:errorWrapper 入参', 'json-schema', 1, 'PLATFORM'),

-- 8. builtin:fromJson 入参
('builtin:fromJson:param', 'INPUT',
 '{"type":"object","properties":{"json":{"type":"string","description":"要解析的 JSON 字符串（directInput 为 null 时使用）"},"type":{"type":"string","enum":["auto","map","list"],"default":"auto"}}}',
 '内置函数 builtin:fromJson 入参', 'json-schema', 1, 'PLATFORM'),

-- 9. builtin:httpCall 入参
('builtin:httpCall:param', 'INPUT',
 '{"type":"object","required":["url"],"properties":{"url":{"type":"string"},"method":{"type":"string","enum":["GET","POST","PUT","DELETE","PATCH"],"default":"GET"},"headers":{"type":"object"},"bodyTemplate":{"type":"string","description":"POST/PUT 请求体 JSON"},"timeout":{"type":"integer","default":5000},"responseType":{"type":"string","enum":["json","text"],"default":"json"}}}',
 '内置函数 builtin:httpCall 入参', 'json-schema', 1, 'PLATFORM'),

-- 10. builtin:jsonExtract 入参
('builtin:jsonExtract:param', 'INPUT',
 '{"type":"object","required":["mappings"],"properties":{"mappings":{"type":"object","description":"字段映射：{ 目标字段名: JsonPath表达式 }","additionalProperties":{"type":"string"}},"defaultOnMissing":{"type":"boolean","default":false,"description":"路径不存在时是否置 null"}}}',
 '内置函数 builtin:jsonExtract 入参', 'json-schema', 1, 'PLATFORM'),

-- 11. builtin:jsonTransform 入参
('builtin:jsonTransform:param', 'INPUT',
 '{"type":"object","required":["expression"],"properties":{"expression":{"type":"string","description":"JsonPath 表达式"},"defaultValue":{"description":"路径不存在时的默认值"}}}',
 '内置函数 builtin:jsonTransform 入参', 'json-schema', 1, 'PLATFORM'),

-- 12. builtin:loopAggregator 入参
('builtin:loopAggregator:param', 'INPUT',
 '{"type":"object","properties":{"mode":{"type":"string","enum":["list","merge","first","last"],"default":"list"}}}',
 '内置函数 builtin:loopAggregator 入参', 'json-schema', 1, 'PLATFORM'),

-- 13. builtin:mqPublish 入参
('builtin:mqPublish:param', 'INPUT',
 '{"type":"object","required":["topic"],"properties":{"topic":{"type":"string","description":"目标 Topic"},"key":{"type":"string","description":"消息 Key（分区路由）"},"value":{"description":"消息体（未配置时使用上游节点输出）"},"async":{"type":"boolean","default":false,"description":"是否异步发送"}}}',
 '内置函数 builtin:mqPublish 入参', 'json-schema', 1, 'PLATFORM'),

-- 14. builtin:paginate 入参
('builtin:paginate:param', 'INPUT',
 '{"type":"object","required":["total"],"properties":{"page":{"type":"integer","default":1},"size":{"type":"integer","default":20},"total":{"type":"integer","description":"总记录数"}}}',
 '内置函数 builtin:paginate 入参', 'json-schema', 1, 'PLATFORM'),

-- 15. builtin:paramValidate 入参
('builtin:paramValidate:param', 'INPUT',
 '{"type":"object","required":["schema"],"properties":{"schema":{"type":"object","title":"JSON Schema","description":"JSON Schema 定义，用于校验工作流入参","x-widget":"jsonCode"}}}',
 '内置函数 builtin:paramValidate 入参', 'json-schema', 1, 'PLATFORM'),

-- 16. builtin:responseWrapper 入参
('builtin:responseWrapper:param', 'INPUT',
 '{"type":"object","properties":{"code":{"type":"integer","default":200},"message":{"type":"string","default":"success"},"data":{"description":"响应数据（未配置时使用上游节点输出）"}}}',
 '内置函数 builtin:responseWrapper 入参', 'json-schema', 1, 'PLATFORM'),

-- 17. builtin:toJson 入参
('builtin:toJson:param', 'INPUT',
 '{"type":"object","properties":{"pretty":{"type":"boolean","default":false,"description":"是否格式化输出（缩进）"},"nullValues":{"type":"boolean","default":true,"description":"是否保留 null 值字段"}}}',
 '内置函数 builtin:toJson 入参', 'json-schema', 1, 'PLATFORM'),

-- 18. builtin:convert 入参
('builtin:convert:param', 'INPUT',
 '{"type":"object","required":["targetType"],"properties":{"targetType":{"type":"string","enum":["map","json","list","pojo"],"description":"目标类型"},"targetClass":{"type":"string","description":"目标 POJO 类全限定名（targetType=pojo 时必填）"},"source":{"description":"源数据（directInput 为 null 时使用）"},"pretty":{"type":"boolean","default":false,"description":"targetType=json 时是否美化输出"}}}',
 '内置函数 builtin:convert 入参', 'json-schema', 1, 'PLATFORM'),

-- 19. builtin:waitForSignal 入参
('builtin:waitForSignal:param', 'INPUT',
 '{"type":"object","required":["signalName"],"properties":{"signalName":{"type":"string","description":"信号名称（与外部发送的信号名匹配）"},"timeoutMs":{"type":"integer","default":0,"description":"超时毫秒数（0=无限等待）"}}}',
 '内置函数 builtin:waitForSignal 入参', 'json-schema', 1, 'PLATFORM');
