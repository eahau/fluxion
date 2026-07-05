package com.fluxion.builtin.meta

import com.fluxion.core.value.FunctionMeta

/**
 * 内置函数元信息常量（由代码生成，禁止手动修改；schema 变更请修改对应函数类后重新生成）
 */
object BuiltinFunctionMetas {

    @JvmField
    val CACHE_GET = FunctionMeta.builder("builtin:cacheGet")
        .description("从缓存读取数据，不存在返回 null")
        .descriptions(mapOf("zh" to "从缓存读取数据，不存在返回 null", "en" to "Read data from cache, returns null if not found"))
        .domain("cache")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "key"
        ],
        "properties": {
                "key": {
                        "type": "string",
                        "description": "缓存键，支持 {paramName} 模板"
                }
        }
}
        """)
        .build()

    @JvmField
    val CACHE_SET = FunctionMeta.builder("builtin:cacheSet")
        .description("写入缓存，返回 true")
        .descriptions(mapOf("zh" to "写入缓存，返回 true", "en" to "Write to cache, returns true"))
        .paramSchema("""
{
        "type": "object",
        "required": [
                "key"
        ],
        "properties": {
                "key": {
                        "type": "string",
                        "description": "缓存键，支持 {paramName} 模板"
                },
                "ttlSeconds": {
                        "type": "integer",
                        "default": 3600
                }
        }
}
        """)
        .build()

    @JvmField
    val CONDITION_BRANCH = FunctionMeta.builder("builtin:conditionBranch")
        .description("条件分支计算，按顺序匹配规则列表，首个命中的条件决定跳转目标节点")
        .descriptions(mapOf("zh" to "条件分支计算，按顺序匹配规则列表，首个命中的条件决定跳转目标节点", "en" to "Conditional branching, evaluates rule list in order, first matched condition determines target node"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "conditions"
        ],
        "properties": {
                "conditions": {
                        "type": "array",
                        "description": "条件列表，按顺序匹配，首个命中的条件决定跳转目标",
                        "items": {
                                "type": "object",
                                "properties": {
                                        "logic": {
                                                "type": "string",
                                                "description": "规则组合逻辑：and（全部满足）或 or（任一满足），默认 and",
                                                "enum": ["and", "or"],
                                                "default": "and"
                                        },
                                        "rules": {
                                                "type": "array",
                                                "description": "规则列表，1 条规则为单条件，多条规则按 logic 组合",
                                                "items": {
                                                        "type": "object",
                                                        "required": ["field", "operator"],
                                                        "properties": {
                                                                "field": {
                                                                        "type": "string",
                                                                        "description": "字段名，支持嵌套路径如 user.age、items"
                                                                },
                                                                "operator": {
                                                                        "type": "string",
                                                                        "description": "操作符（否定语义通过 negate=true 实现，如 eq+negate = 不等于）",
                                                                        "enum": ["eq", "gt", "gte", "lt", "lte", "in", "contains", "isNull", "startsWith", "endsWith", "regex"]
                                                                },
                                                                "value": {
                                                                        "type": "string",
                                                                        "description": "比较目标值，in 操作符支持逗号分隔；isNull 操作符无需填写"
                                                                },
                                                                "negate": {
                                                                        "type": "boolean",
                                                                        "description": "是否对当前规则结果取反",
                                                                        "default": false
                                                                }
                                                        }
                                                }
                                        },
                                        "negate": {
                                                "type": "boolean",
                                                "description": "是否对当前条件整体结果取反",
                                                "default": false
                                        },
                                        "condition": {
                                                "type": "string",
                                                "description": "JEXL 表达式（高级选项，rules 为空时生效）"
                                        },
                                        "target": {
                                                "type": "string",
                                                "description": "条件匹配时跳转的目标节点 ID"
                                        }
                                }
                        }
                },
                "defaultTarget": {
                        "type": "string",
                        "description": "所有条件均未匹配时跳转的默认目标节点 ID"
                }
        }
}
        """)
        .build()

    @JvmField
    val FILTER = FunctionMeta.builder("builtin:filter")
        .description("条件过滤，根据规则组合结果返回 true/false，配合条件分支使用")
        .descriptions(mapOf("zh" to "条件过滤，根据规则组合结果返回 true/false，配合条件分支使用", "en" to "Conditional filter, returns true/false based on rule combination for condition branching"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "logic": {
                        "type": "string",
                        "description": "规则组合逻辑：and（全部满足）或 or（任一满足），默认 and",
                        "enum": ["and", "or"],
                        "default": "and"
                },
                "rules": {
                        "type": "array",
                        "description": "规则列表，1 条规则为单条件，多条规则按 logic 组合",
                        "items": {
                                "type": "object",
                                "required": ["field", "operator"],
                                "properties": {
                                        "field": {
                                                "type": "string",
                                                "description": "字段名，支持嵌套路径如 user.age、items"
                                        },
                                        "operator": {
                                                "type": "string",
                                                "description": "操作符（否定语义通过 negate=true 实现，如 eq+negate = 不等于）",
                                                "enum": ["eq", "gt", "gte", "lt", "lte", "in", "contains", "isNull", "startsWith", "endsWith", "regex"]
                                        },
                                        "value": {
                                                "type": "string",
                                                "description": "比较目标值，in 操作符支持逗号分隔；isNull 操作符无需填写"
                                        },
                                        "negate": {
                                                "type": "boolean",
                                                "description": "是否对当前规则结果取反",
                                                "default": false
                                        }
                                }
                        }
                },
                "negate": {
                        "type": "boolean",
                        "description": "是否对整体条件结果取反",
                        "default": false
                },
                "condition": {
                        "type": "string",
                        "description": "JEXL 表达式（高级选项，rules 为空时生效）"
                }
        }
}
        """)
        .build()

    @JvmField
    val DB_EXECUTE = FunctionMeta.builder("builtin:dbExecute")
        .description("执行数据库 SQL（SELECT/INSERT/UPDATE/DELETE），自动识别查询或写操作")
        .descriptions(mapOf("zh" to "执行数据库 SQL（SELECT/INSERT/UPDATE/DELETE），自动识别查询或写操作", "en" to "Execute database SQL (SELECT/INSERT/UPDATE/DELETE), auto-detects query or write operation"))
        .domain("db")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "sql"
        ],
        "properties": {
                "sql": {
                        "type": "string",
                        "description": "SQL 语句，支持 :paramName 命名参数和 ${'$'}{template} 表名模板",
                        "x-widget": "dbSqlEditor",
                        "x-i18n": {
                                "zh": { "description": "SQL 语句，支持 :paramName 命名参数和 ${'$'}{template} 表名模板，可执行 SELECT/INSERT/UPDATE/DELETE/DDL 等任意 SQL" },
                                "en": { "description": "SQL statement, supports :paramName named parameters and ${'$'}{template} table name templates for SELECT/INSERT/UPDATE/DELETE/DDL" }
                        }
                },
                "params": {
                        "type": "object",
                        "description": "SQL 参数绑定：键为 :paramName 参数名，值为字面量或 {${'$'}ref} 引用上游节点输出",
                        "additionalProperties": true,
                        "x-i18n": {
                                "zh": { "description": "SQL 参数绑定：键为 :paramName 参数名，值为字面量或 {${'$'}ref} 引用上游节点输出字段" },
                                "en": { "description": "SQL parameter bindings: key is :paramName, value is literal or {${'$'}ref} referencing upstream node output" }
                        }
                },
                "dataSource": {
                        "type": "string",
                        "default": "default",
                        "description": "数据源名称",
                        "x-i18n": {
                                "zh": { "description": "数据源名称，多数据源场景使用，如 default / order / user" },
                                "en": { "description": "Data source name, used in multi-datasource scenarios, e.g. default / order / user" }
                        }
                },
                "resultType": {
                        "type": "string",
                        "enum": [
                                "list",
                                "one",
                                "count"
                        ],
                        "default": "list",
                        "description": "查询结果类型（仅 SELECT/WITH 生效）",
                        "x-i18n": {
                                "zh": { "description": "查询结果类型（仅 SELECT/WITH 生效）：list 返回列表、one 返回单条、count 返回行数" },
                                "en": { "description": "Query result type (SELECT/WITH only): list returns array, one returns single row, count returns row count" }
                        }
                },
                "limit": {
                        "type": "integer",
                        "default": 1000,
                        "description": "查询最大返回行数，默认 1000；<=0 表示不限",
                        "x-i18n": {
                                "zh": { "description": "查询最大返回行数，默认 1000；<=0 表示不限" },
                                "en": { "description": "Max rows to return for queries, default 1000; <=0 means unlimited" }
                        }
                },
                "compensateSql": {
                        "type": "string",
                        "description": "Saga 补偿 SQL（写操作逆操作）",
                        "x-widget": "dbSqlEditor",
                        "x-i18n": {
                                "zh": { "description": "Saga 补偿 SQL（写操作失败时执行的逆操作 SQL）" },
                                "en": { "description": "Saga compensation SQL (reverse operation SQL executed on write failure)" }
                        }
                },
                "compensateFunctionRef": {
                        "type": "string",
                        "description": "Saga 补偿函数引用",
                        "x-i18n": {
                                "zh": { "description": "Saga 补偿函数引用（与 compensateSql 二选一）" },
                                "en": { "description": "Saga compensation function reference (mutually exclusive with compensateSql)" }
                        }
                }
        }
}
        """)
        .outputSchema("""
{
        "type": "object",
        "description": "查询返回 list/map/count，写操作返回生成键或影响行数",
        "x-i18n": {
                "zh": { "description": "SELECT 查询：resultType=list 返回 [{col:val},...]、one 返回 {col:val}、count 返回数字；写操作返回自增主键或受影响行数" },
                "en": { "description": "SELECT query: resultType=list returns [{col:val},...], one returns {col:val}, count returns number; write ops return generated key or affected rows" }
        }
}
        """)
        .build()

    @JvmField
    val ERROR_WRAPPER = FunctionMeta.builder("builtin:errorWrapper")
        .description("统一错误响应封装，返回 {code, message, data} 格式")
        .descriptions(mapOf("zh" to "统一错误响应封装，返回 {code, message, data} 格式", "en" to "Unified error response wrapper, returns {code, message, data} format"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "code": {
                        "type": "integer",
                        "default": 500
                },
                "message": {
                        "type": "string",
                        "description": "错误消息（未配置时尝试从输入提取）"
                },
                "data": {
                        "description": "错误附加数据（未配置时使用上游节点输出）"
                }
        }
}
        """)
        .build()

    @JvmField
    val FROM_JSON = FunctionMeta.builder("builtin:fromJson")
        .description("解析 JSON 字符串为 Map/List/Object，供工作流节点内部消费")
        .descriptions(mapOf("zh" to "解析 JSON 字符串为 Map/List/Object，供工作流节点内部消费", "en" to "Parse JSON string to Map/List/Object for internal workflow node consumption"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "json": {
                        "type": "string",
                        "description": "要解析的 JSON 字符串（directInput 为 null 时使用）"
                },
                "type": {
                        "type": "string",
                        "enum": [
                                "auto",
                                "map",
                                "list"
                        ],
                        "default": "auto"
                }
        }
}
        """)
        .build()

    @JvmField
    val HTTP_CALL = FunctionMeta.builder("builtin:httpCall")
        .description("发起 HTTP 请求，支持 GET/POST/PUT/DELETE，自动解析 JSON 响应")
        .descriptions(mapOf("zh" to "发起 HTTP 请求，支持 GET/POST/PUT/DELETE，自动解析 JSON 响应", "en" to "Send HTTP request, supports GET/POST/PUT/DELETE with auto JSON response parsing"))
        .domain("http")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "url"
        ],
        "properties": {
                "url": {
                        "type": "string"
                },
                "method": {
                        "type": "string",
                        "enum": [
                                "GET",
                                "POST",
                                "PUT",
                                "DELETE",
                                "PATCH"
                        ],
                        "default": "GET"
                },
                "headers": {
                        "type": "object"
                },
                "bodyTemplate": {
                        "type": "string",
                        "description": "POST/PUT 请求体 JSON"
                },
                "timeout": {
                        "type": "integer",
                        "default": 5000
                },
                "responseType": {
                        "type": "string",
                        "enum": [
                                "json",
                                "text"
                        ],
                        "default": "json"
                }
        }
}
        """)
        .build()

    @JvmField
    val JSON_EXTRACT = FunctionMeta.builder("builtin:jsonExtract")
        .description("使用 JsonPath 从输入中提取字段，构造新的 Map 输出")
        .descriptions(mapOf("zh" to "使用 JsonPath 从输入中提取字段，构造新的 Map 输出", "en" to "Extract fields from input using JsonPath, construct new Map output"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "mappings"
        ],
        "properties": {
                "mappings": {
                        "type": "object",
                        "description": "字段映射：{ 目标字段名: JsonPath表达式 }",
                        "additionalProperties": {
                                "type": "string"
                        }
                },
                "defaultOnMissing": {
                        "type": "boolean",
                        "default": false,
                        "description": "路径不存在时是否置 null"
                }
        }
}
        """)
        .build()

    @JvmField
    val JSON_TRANSFORM = FunctionMeta.builder("builtin:jsonTransform")
        .description("使用 JsonPath 表达式提取/转换 JSON 数据")
        .descriptions(mapOf("zh" to "使用 JsonPath 表达式提取/转换 JSON 数据", "en" to "Extract/transform JSON data using JsonPath expressions"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "expression"
        ],
        "properties": {
                "expression": {
                        "type": "string",
                        "description": "JsonPath 表达式"
                },
                "defaultValue": {
                        "description": "路径不存在时的默认值"
                }
        }
}
        """)
        .build()

    @JvmField
    val LOOP_AGGREGATOR = FunctionMeta.builder("builtin:loopAggregator")
        .description("聚合并行节点输出，支持 DAG dependsOn 多输入与线性流单输入；模式：list/merge/first/last")
        .descriptions(mapOf("zh" to "聚合并行节点输出，支持 DAG dependsOn 多输入与线性流单输入；模式：list/merge/first/last", "en" to "Aggregate parallel node outputs, supports DAG dependsOn multi-input and linear flow; modes: list/merge/first/last"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "mode": {
                        "type": "string",
                        "enum": [
                                "list",
                                "merge",
                                "first",
                                "last"
                        ],
                        "default": "list"
                }
        }
}
        """)
        .build()

    @JvmField
    val MQ_PUBLISH = FunctionMeta.builder("builtin:mqPublish")
        .description("向 MQ Topic 发送消息，支持同步/异步两种模式")
        .descriptions(mapOf("zh" to "向 MQ Topic 发送消息，支持同步/异步两种模式", "en" to "Publish message to MQ Topic, supports sync/async modes"))
        .domain("mq")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "topic"
        ],
        "properties": {
                "topic": {
                        "type": "string",
                        "description": "目标 Topic"
                },
                "key": {
                        "type": "string",
                        "description": "消息 Key（分区路由）"
                },
                "value": {
                        "description": "消息体（未配置时使用上游节点输出）"
                },
                "async": {
                        "type": "boolean",
                        "default": false,
                        "description": "是否异步发送"
                }
        }
}
        """)
        .build()

    @JvmField
    val PAGINATE = FunctionMeta.builder("builtin:paginate")
        .description("分页结果封装，返回 {items, page, size, total, totalPages, hasNext, hasPrev}")
        .descriptions(mapOf("zh" to "分页结果封装，返回 {items, page, size, total, totalPages, hasNext, hasPrev}", "en" to "Pagination wrapper, returns {items, page, size, total, totalPages, hasNext, hasPrev}"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "total"
        ],
        "properties": {
                "page": {
                        "type": "integer",
                        "default": 1
                },
                "size": {
                        "type": "integer",
                        "default": 20
                },
                "total": {
                        "type": "integer",
                        "description": "总记录数"
                }
        }
}
        """)
        .build()

    @JvmField
    val PARAM_VALIDATE = FunctionMeta.builder("builtin:paramValidate")
        .description("JSON Schema 参数校验，校验通过透传入参，失败抛出异常")
        .descriptions(mapOf("zh" to "JSON Schema 参数校验，校验通过透传入参，失败抛出异常", "en" to "JSON Schema parameter validation, passes through on success, throws on failure"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "schema"
        ],
        "properties": {
                "schema": {
                        "type": "object",
                        "title": "JSON Schema",
                        "description": "JSON Schema 定义，用于校验工作流入参",
                        "x-widget": "jsonCode"
                }
        }
}
        """)
        .build()

    @JvmField
    val RESPONSE_WRAPPER = FunctionMeta.builder("builtin:responseWrapper")
        .description("统一 API 响应封装，返回 {code, message, data} 格式")
        .descriptions(mapOf("zh" to "统一 API 响应封装，返回 {code, message, data} 格式", "en" to "Unified API response wrapper, returns {code, message, data} format"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "code": {
                        "type": "integer",
                        "default": 200
                },
                "message": {
                        "type": "string",
                        "default": "success"
                },
                "data": {
                        "description": "响应数据（未配置时使用上游节点输出）"
                }
        }
}
        """)
        .build()

    @JvmField
    val TO_JSON = FunctionMeta.builder("builtin:toJson")
        .description("将节点输出（任意对象）序列化为 JSON 字符串")
        .descriptions(mapOf("zh" to "将节点输出（任意对象）序列化为 JSON 字符串", "en" to "Serialize node output (any object) to JSON string"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "properties": {
                "pretty": {
                        "type": "boolean",
                        "default": false,
                        "description": "是否格式化输出（缩进）"
                },
                "nullValues": {
                        "type": "boolean",
                        "default": true,
                        "description": "是否保留 null 值字段"
                }
        }
}
        """)
        .build()

    @JvmField
    val CONVERT = FunctionMeta.builder("builtin:convert")
        .description("在 Map / POJO / JSON 字符串 / List 之间进行类型转换")
        .descriptions(mapOf("zh" to "在 Map / POJO / JSON 字符串 / List 之间进行类型转换", "en" to "Type conversion between Map / POJO / JSON string / List"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "targetType"
        ],
        "properties": {
                "targetType": {
                        "type": "string",
                        "enum": [
                                "map",
                                "json",
                                "list",
                                "pojo"
                        ],
                        "description": "目标类型"
                },
                "targetClass": {
                        "type": "string",
                        "description": "目标 POJO 类全限定名（targetType=pojo 时必填）"
                },
                "source": {
                        "description": "源数据（directInput 为 null 时使用）"
                },
                "pretty": {
                        "type": "boolean",
                        "default": false,
                        "description": "targetType=json 时是否美化输出"
                }
        }
}
        """)
        .build()

    @JvmField
    val WAIT_FOR_SIGNAL = FunctionMeta.builder("builtin:waitForSignal")
        .description("阻塞等待外部信号（approve/reject/回调等），信号到达后返回 payload")
        .descriptions(mapOf("zh" to "阻塞等待外部信号（approve/reject/回调等），信号到达后返回 payload", "en" to "Block and wait for external signal (approve/reject/callback), returns payload when received"))
        .domain("common")
        .paramSchema("""
{
        "type": "object",
        "required": [
                "signalName"
        ],
        "properties": {
                "signalName": {
                        "type": "string",
                        "description": "信号名称（与外部发送的信号名匹配）"
                },
                "timeoutMs": {
                        "type": "integer",
                        "default": 0,
                        "description": "超时毫秒数（0=无限等待）"
                }
        }
}
        """)
        .build()
}
