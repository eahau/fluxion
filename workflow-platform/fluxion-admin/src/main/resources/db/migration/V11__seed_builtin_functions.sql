-- ============================================================
-- V11：内置函数（builtin:*）元数据种子化到 wf_function 表
--   作为「管理面」单一数据源（函数列表展示 / 前端分组 / i18n 描述持久化）。
--   执行面（Worker 运行时 FunctionMeta）仍由代码 meta() 常量提供，不读库；
--   因此本种子与运行期解耦，仅用于 Admin 管理展示，可运维覆盖。
--   使用 INSERT IGNORE：重复执行安全，不覆盖用户可能修改的行。
--   paramSchemaRef / outputSchemaRef 复用 V9 已种子的 wf_schema.schema_name。
-- ============================================================

INSERT IGNORE INTO `wf_function`
    (`function_name`, `scope`, `app_group`, `source_ref`, `function_type`, `domain`, `config`, `status`, `category`)
VALUES

-- 流程控制
('builtin:conditionBranch','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"条件分支计算，按顺序匹配规则列表，首个命中的条件决定跳转目标节点","paramSchemaRef":"builtin:conditionBranch:param","descriptions":{"zh":"条件分支计算，按顺序匹配规则列表，首个命中的条件决定跳转目标节点","en":"Conditional branching, evaluates rule list in order, first matched condition determines target node"}}',
 'ACTIVE','flow-control'),

('builtin:filter','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"条件过滤，根据规则组合结果返回 true/false，配合条件分支使用","paramSchemaRef":"builtin:filter:param","descriptions":{"zh":"条件过滤，根据规则组合结果返回 true/false，配合条件分支使用","en":"Conditional filter, returns true/false based on rule combination for condition branching"}}',
 'ACTIVE','flow-control'),

('builtin:loopAggregator','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"聚合并行节点输出，支持 DAG dependsOn 多输入与线性流单输入；模式：list/merge/first/last","paramSchemaRef":"builtin:loopAggregator:param","descriptions":{"zh":"聚合并行节点输出，支持 DAG dependsOn 多输入与线性流单输入；模式：list/merge/first/last","en":"Aggregate parallel node outputs, supports DAG dependsOn multi-input and linear flow; modes: list/merge/first/last"}}',
 'ACTIVE','flow-control'),

-- 数据访问
('builtin:dbExecute','PLATFORM',NULL,NULL,'BUILTIN','db',
 '{"description":"执行数据库 SQL（SELECT/INSERT/UPDATE/DELETE），自动识别查询或写操作","paramSchemaRef":"builtin:dbExecute:param","outputSchemaRef":"builtin:dbExecute:output","descriptions":{"zh":"执行数据库 SQL（SELECT/INSERT/UPDATE/DELETE），自动识别查询或写操作","en":"Execute database SQL (SELECT/INSERT/UPDATE/DELETE), auto-detects query or write operation"}}',
 'ACTIVE','data-access'),

('builtin:cacheGet','PLATFORM',NULL,NULL,'BUILTIN','cache',
 '{"description":"从缓存读取数据，不存在返回 null","paramSchemaRef":"builtin:cacheGet:param","descriptions":{"zh":"从缓存读取数据，不存在返回 null","en":"Read data from cache, returns null if not found"}}',
 'ACTIVE','data-access'),

('builtin:cacheSet','PLATFORM',NULL,NULL,'BUILTIN','cache',
 '{"description":"写入缓存，返回 true","paramSchemaRef":"builtin:cacheSet:param","descriptions":{"zh":"写入缓存，返回 true","en":"Write to cache, returns true"}}',
 'ACTIVE','data-access'),

('builtin:redisCommand','PLATFORM',NULL,NULL,'BUILTIN','redis',
 '{"description":"通用 Redis 命令执行器","descriptions":{"zh":"通用 Redis 命令执行器","en":"Generic Redis command executor"}}',
 'ACTIVE','data-access'),

('builtin:httpCall','PLATFORM',NULL,NULL,'BUILTIN','http',
 '{"description":"发起 HTTP 请求，支持 GET/POST/PUT/DELETE，自动解析 JSON 响应","paramSchemaRef":"builtin:httpCall:param","descriptions":{"zh":"发起 HTTP 请求，支持 GET/POST/PUT/DELETE，自动解析 JSON 响应","en":"Send HTTP request, supports GET/POST/PUT/DELETE with auto JSON response parsing"}}',
 'ACTIVE','data-access'),

('builtin:mqPublish','PLATFORM',NULL,NULL,'BUILTIN','mq',
 '{"description":"向 MQ Topic 发送消息，支持同步/异步两种模式","paramSchemaRef":"builtin:mqPublish:param","descriptions":{"zh":"向 MQ Topic 发送消息，支持同步/异步两种模式","en":"Publish message to MQ Topic, supports sync/async modes"}}',
 'ACTIVE','data-access'),

-- 数据处理
('builtin:toJson','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"将节点输出（任意对象）序列化为 JSON 字符串","paramSchemaRef":"builtin:toJson:param","descriptions":{"zh":"将节点输出（任意对象）序列化为 JSON 字符串","en":"Serialize node output (any object) to JSON string"}}',
 'ACTIVE','data-processing'),

('builtin:fromJson','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"解析 JSON 字符串为 Map/List/Object，供工作流节点内部消费","paramSchemaRef":"builtin:fromJson:param","descriptions":{"zh":"解析 JSON 字符串为 Map/List/Object，供工作流节点内部消费","en":"Parse JSON string to Map/List/Object for internal workflow node consumption"}}',
 'ACTIVE','data-processing'),

('builtin:jsonExtract','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"使用 JsonPath 从输入中提取字段，构造新的 Map 输出","paramSchemaRef":"builtin:jsonExtract:param","descriptions":{"zh":"使用 JsonPath 从输入中提取字段，构造新的 Map 输出","en":"Extract fields from input using JsonPath, construct new Map output"}}',
 'ACTIVE','data-processing'),

('builtin:jsonTransform','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"使用 JsonPath 表达式提取/转换 JSON 数据","paramSchemaRef":"builtin:jsonTransform:param","descriptions":{"zh":"使用 JsonPath 表达式提取/转换 JSON 数据","en":"Extract/transform JSON data using JsonPath expressions"}}',
 'ACTIVE','data-processing'),

('builtin:convert','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"在 Map / POJO / JSON 字符串 / List 之间进行类型转换","paramSchemaRef":"builtin:convert:param","descriptions":{"zh":"在 Map / POJO / JSON 字符串 / List 之间进行类型转换","en":"Type conversion between Map / POJO / JSON string / List"}}',
 'ACTIVE','data-processing'),

('builtin:paginate','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"分页结果封装，返回 {items, page, size, total, totalPages, hasNext, hasPrev}","paramSchemaRef":"builtin:paginate:param","descriptions":{"zh":"分页结果封装，返回 {items, page, size, total, totalPages, hasNext, hasPrev}","en":"Pagination wrapper, returns {items, page, size, total, totalPages, hasNext, hasPrev}"}}',
 'ACTIVE','data-processing'),

-- 数据校验
('builtin:paramValidate','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"JSON Schema 参数校验，校验通过透传入参，失败抛出异常","paramSchemaRef":"builtin:paramValidate:param","descriptions":{"zh":"JSON Schema 参数校验，校验通过透传入参，失败抛出异常","en":"JSON Schema parameter validation, passes through on success, throws on failure"}}',
 'ACTIVE','validation'),

-- 响应处理
('builtin:responseWrapper','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"统一 API 响应封装，返回 {code, message, data} 格式","paramSchemaRef":"builtin:responseWrapper:param","descriptions":{"zh":"统一 API 响应封装，返回 {code, message, data} 格式","en":"Unified API response wrapper, returns {code, message, data} format"}}',
 'ACTIVE','response'),

('builtin:errorWrapper','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"统一错误响应封装，返回 {code, message, data} 格式","paramSchemaRef":"builtin:errorWrapper:param","descriptions":{"zh":"统一错误响应封装，返回 {code, message, data} 格式","en":"Unified error response wrapper, returns {code, message, data} format"}}',
 'ACTIVE','response'),

-- 脚本执行
('builtin:groovyScript','PLATFORM',NULL,NULL,'BUILTIN','script',
 '{"description":"执行 Groovy 脚本，支持 inline/scriptRef 双模式，Caffeine 编译缓存","descriptions":{"zh":"执行 Groovy 脚本，支持 inline/scriptRef 双模式，Caffeine 编译缓存","en":"Execute Groovy script, supports inline/scriptRef dual mode with Caffeine compile cache"}}',
 'ACTIVE','script'),

-- 外部信号
('builtin:waitForSignal','PLATFORM',NULL,NULL,'BUILTIN','common',
 '{"description":"阻塞等待外部信号（approve/reject/回调等），信号到达后返回 payload","paramSchemaRef":"builtin:waitForSignal:param","descriptions":{"zh":"阻塞等待外部信号（approve/reject/回调等），信号到达后返回 payload","en":"Block and wait for external signal (approve/reject/callback), returns payload when received"}}',
 'ACTIVE','flow-control');
