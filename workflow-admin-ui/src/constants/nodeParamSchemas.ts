export const NODE_PARAM_SCHEMAS: Record<string, any> = {
  PARAM_VALIDATE: {
    type: 'object',
    title: '入参校验配置',
    required: ['schema'],
    properties: {
      schema: {
        type: 'object',
        title: 'JSON Schema',
        description: 'JSON Schema 定义，用于校验工作流入参',
        'x-widget': 'jsonCode',
      },
    },
  },
  DYNAMIC_VALIDATE: {
    type: 'object',
    title: '动态校验配置',
    properties: {
      expression: { type: 'string', title: '校验表达式' },
      errorMessage: { type: 'string', title: '错误提示' },
    },
  },
  DATA_QUERY: {
    type: 'object',
    title: '数据查询配置',
    properties: {
      dataSource: { type: 'string', title: '数据源' },
      operation: {
        type: 'string',
        title: '操作类型',
        enum: ['SQL', 'GET', 'POST', 'RPC'],
        default: 'SQL',
      },
      statement: { type: 'string', title: 'SQL/语句' },
      params: { type: 'object', title: '查询参数' },
    },
  },
  DATA_TRANSFORM: {
    type: 'object',
    title: '数据转换配置',
    properties: {
      mapping: { type: 'object', title: '字段映射' },
      expression: { type: 'string', title: '转换表达式' },
    },
  },
  ASSEMBLE_RESPONSE: {
    type: 'object',
    title: '组装响应配置',
    properties: {
      template: { type: 'string', title: '响应模板' },
      fields: {
        type: 'array',
        title: '响应字段',
        items: {
          type: 'object',
          properties: {
            name: { type: 'string', title: '字段名' },
            source: { type: 'string', title: '数据来源' },
          },
        },
      },
    },
  },
  SCRIPT: {
    type: 'object',
    title: '脚本执行配置',
    properties: {
      engine: {
        type: 'string',
        title: '脚本引擎',
        enum: ['groovy', 'javascript', 'python'],
        default: 'groovy',
      },
      script: { type: 'string', title: '脚本源码' },
    },
  },
  CUSTOM: {
    type: 'object',
    title: '自定义参数',
    properties: {
      config: { type: 'object', title: '自定义配置' },
    },
  },
  CONDITION_BRANCH: {
    type: 'object',
    title: '条件分支配置',
    required: ['conditions'],
    properties: {
      globalLogicEnabled: {
        type: 'boolean',
        title: '启用全局逻辑（条件列表）',
        description: '勾选后，所有条件项之间强制使用统一的 AND/OR 逻辑',
        default: false,
      },
      globalLogicOperator: {
        type: 'string',
        title: '全局逻辑运算符（条件列表）',
        description: '仅当启用全局逻辑时生效：AND=所有条件同时满足，OR=任一条件满足',
        enum: ['and', 'or'],
        enumNames: ['全部满足 (AND)', '任一满足 (OR)'],
        default: 'and',
      },
      conditions: {
        type: 'array',
        title: '条件列表',
        description: '按顺序匹配，首个命中的条件决定跳转目标',
        items: {
          type: 'object',
          properties: {
            logic: {
              type: 'string',
              title: '组合逻辑',
              description: '本条件项内多条规则的默认组合逻辑（当未启用规则级独立逻辑时使用）',
              enum: ['and', 'or'],
              enumNames: ['全部满足 (AND)', '任一满足 (OR)'],
              default: 'and',
            },
            rulesGlobalLogicEnabled: {
              type: 'boolean',
              title: '启用全局逻辑（规则列表）',
              description: '勾选后，本条件项内所有规则强制使用统一的 AND/OR 逻辑',
              default: false,
            },
            rulesGlobalLogicOperator: {
              type: 'string',
              title: '全局逻辑运算符（规则列表）',
              description: '仅当启用规则全局逻辑时生效：AND=所有规则同时满足，OR=任一规则满足',
              enum: ['and', 'or'],
              enumNames: ['全部满足 (AND)', '任一满足 (OR)'],
              default: 'and',
            },
            rules: {
              type: 'array',
              title: '规则列表',
              description: '1 条规则为单条件；从第 2 条起可独立选择与上一条规则之间的 AND/OR',
              items: {
                type: 'object',
                required: ['field', 'operator'],
                properties: {
                  logic: {
                    type: 'string',
                    title: '与上一规则的逻辑',
                    description: '本条规则与其前一条规则之间的逻辑关系（第 1 条规则忽略此字段）',
                    enum: ['and', 'or'],
                    enumNames: ['AND (同时)', 'OR (任一)'],
                    default: 'and',
                  },
                  field: {
                    type: 'string',
                    title: '字段名',
                    description: '上游输出中的字段名，支持嵌套路径如 user.age、items',
                  },
                  operator: {
                    type: 'string',
                    title: '操作符',
                    description: '否定语义通过 negate 开关实现，如 eq + negate = 不等于；按字段类型智能限定可用操作符',
                    enum: ['eq', 'gt', 'gte', 'lt', 'lte', 'in', 'contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte', 'startsWith', 'endsWith', 'regex'],
                    enumNames: ['eq (等于)', 'gt (大于)', 'gte (大于等于)', 'lt (小于)', 'lte (小于等于)', 'in (在列表中)', 'contains (包含)', 'isNull (为空)', 'isEmpty (空集合)', 'sizeEq (长度等于)', 'sizeGt (长度大于)', 'sizeGte (长度大于等于)', 'sizeLt (长度小于)', 'sizeLte (长度小于等于)', 'startsWith (以...开头)', 'endsWith (以...结尾)', 'regex (正则匹配)'],
                  },
                  value: {
                    type: 'string',
                    title: '值',
                    description: '比较目标值，in 操作符支持逗号分隔；isNull / isEmpty 操作符无需填写；size* 操作符填整数',
                  },
                  negate: {
                    type: 'boolean',
                    title: '取反',
                    default: false,
                  },
                },
              },
            },
            negate: {
              type: 'boolean',
              title: '整体取反',
              default: false,
            },
            condition: {
              type: 'string',
              title: 'AviatorScript 表达式（高级，完全兼容 JEXL）',
              description: 'rules 为空时生效，可直接写 AviatorScript 表达式（完全兼容 JEXL 语法）',
            },
            target: {
              type: 'string',
              title: '目标节点 ID',
              description: '条件匹配时跳转的目标节点',
            },
          },
        },
      },
      defaultTarget: {
        type: 'string',
        title: '默认目标节点 ID',
        description: '所有条件均未匹配时跳转的目标',
      },
    },
  },
  FILTER: {
    type: 'object',
    title: '条件过滤配置',
    properties: {
      rulesGlobalLogicEnabled: {
        type: 'boolean',
        title: '启用全局逻辑（规则列表）',
        description: '勾选后，所有规则强制使用统一的 AND/OR 逻辑',
        default: false,
      },
      rulesGlobalLogicOperator: {
        type: 'string',
        title: '全局逻辑运算符（规则列表）',
        description: '仅当启用规则全局逻辑时生效：AND=所有规则同时满足，OR=任一规则满足',
        enum: ['and', 'or'],
        enumNames: ['全部满足 (AND)', '任一满足 (OR)'],
        default: 'and',
      },
      logic: {
        type: 'string',
        title: '组合逻辑（默认）',
        description: '未启用规则级独立逻辑 / 全局逻辑时使用的默认组合方式',
        enum: ['and', 'or'],
        enumNames: ['全部满足 (AND)', '任一满足 (OR)'],
        default: 'and',
      },
      rules: {
        type: 'array',
        title: '规则列表',
        description: '1 条规则为单条件；从第 2 条起可独立选择与上一条规则之间的 AND/OR',
        items: {
          type: 'object',
          required: ['field', 'operator'],
          properties: {
            logic: {
              type: 'string',
              title: '与上一规则的逻辑',
              description: '本条规则与其前一条规则之间的逻辑关系（第 1 条规则忽略此字段）',
              enum: ['and', 'or'],
              enumNames: ['AND (同时)', 'OR (任一)'],
              default: 'and',
            },
            field: {
              type: 'string',
              title: '字段名',
              description: '上游输出中的字段名，支持嵌套路径如 user.age、items',
            },
            operator: {
              type: 'string',
              title: '操作符',
              description: '否定语义通过 negate 开关实现，如 eq + negate = 不等于；按字段类型智能限定可用操作符',
              enum: ['eq', 'gt', 'gte', 'lt', 'lte', 'in', 'contains', 'isNull', 'isEmpty', 'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte', 'startsWith', 'endsWith', 'regex'],
              enumNames: ['eq (等于)', 'gt (大于)', 'gte (大于等于)', 'lt (小于)', 'lte (小于等于)', 'in (在列表中)', 'contains (包含)', 'isNull (为空)', 'isEmpty (空集合)', 'sizeEq (长度等于)', 'sizeGt (长度大于)', 'sizeGte (长度大于等于)', 'sizeLt (长度小于)', 'sizeLte (长度小于等于)', 'startsWith (以...开头)', 'endsWith (以...结尾)', 'regex (正则匹配)'],
            },
            value: {
              type: 'string',
              title: '值',
              description: '比较目标值，in 操作符支持逗号分隔；isNull / isEmpty 操作符无需填写；size* 操作符填整数',
            },
            negate: {
              type: 'boolean',
              title: '取反',
              default: false,
            },
          },
        },
      },
      negate: {
        type: 'boolean',
        title: '整体取反',
        default: false,
      },
      condition: {
        type: 'string',
        title: 'AviatorScript 表达式（高级，完全兼容 JEXL）',
        description: 'rules 为空时生效，可直接写 AviatorScript 表达式（完全兼容 JEXL 语法）',
      },
    },
  },
  PARALLEL: {
    type: 'object',
    title: '并行执行配置',
    properties: {
      aggregationStrategy: {
        type: 'string',
        title: '聚合策略',
        enum: ['MERGE', 'FIRST', 'LAST', 'COLLECT'],
        default: 'MERGE',
      },
    },
  },
  SUB_WORKFLOW: {
    type: 'object',
    title: '子工作流配置',
    properties: {
      workflowId: { type: 'string', title: '子工作流 ID' },
      inputMapping: { type: 'object', title: '输入映射' },
    },
  },
  EIP_ROUTER: {
    type: 'object',
    title: 'EIP 路由配置',
    properties: {
      routes: {
        type: 'array',
        title: '路由规则',
        items: {
          type: 'object',
          properties: {
            condition: { type: 'string', title: '路由条件' },
            target: { type: 'string', title: '目标节点' },
          },
        },
      },
    },
  },
  // ── HTTP 函数 ──────────────────────────────────────────────
  'builtin:httpCall': {
    type: 'object',
    title: 'HTTP 请求',
    required: ['url'],
    properties: {
      url: { type: 'string', title: '请求 URL', description: '目标地址' },
      method: {
        type: 'string',
        title: '请求方法',
        enum: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
        default: 'GET',
      },
      headers: { type: 'object', title: '请求头', description: 'HTTP 请求头 {key: value}' },
      bodyTemplate: { type: 'string', title: '请求体 (JSON)', description: 'POST/PUT 请求体 JSON 模板' },
      timeout: { type: 'integer', title: '超时 (ms)', default: 5000 },
      responseType: {
        type: 'string',
        title: '响应类型',
        enum: ['json', 'text'],
        default: 'json',
      },
    },
  },
  'builtin:paginate': {
    type: 'object',
    title: '分页封装',
    required: ['total'],
    properties: {
      page: { type: 'integer', title: '当前页码', default: 1 },
      size: { type: 'integer', title: '每页条数', default: 20 },
      total: { type: 'integer', title: '总记录数' },
    },
  },
  'builtin:redisCommand': {
    type: 'object',
    title: 'Redis 命令',
    // raw 与结构化参数二选一
    required: [],
    properties: {
      raw: {
        type: 'string',
        title: '命令行',
        description: '原始 Redis CLI 命令行（如 SET user:1 name John；支持 ${var} 模板插值）',
        'x-widget': 'textarea',
      },
      command: {
        type: 'string',
        title: 'Redis 命令',
        enum: ['APPEND','BITCOUNT','BITOP','BITPOS','BLPOP','BRPOP','DBSIZE','DECR','DECRBY','DEL','ECHO','EVAL','EXISTS','EXPIRE','EXPIREAT','FLUSHALL','FLUSHDB','GET','GETBIT','GETRANGE','GETSET','HDEL','HEXISTS','HGET','HGETALL','HINCRBY','HKEYS','HLEN','HMGET','HMSET','HSCAN','HSET','HSETNX','HVALS','INCR','INCRBY','INCRBYFLOAT','KEYS','LINDEX','LINSERT','LLEN','LPOP','LPUSH','LPUSHX','LRANGE','LREM','LSET','LTRIM','MGET','MSET','MSETNX','PEXPIRE','PING','PIPELINE','PSETEX','PTTL','RPOP','RPUSH','RPUSHX','SADD','SCAN','SCARD','SDIFF','SDIFFSTORE','SET','SETBIT','SETEX','SETNX','SETRANGE','SINTER','SINTERSTORE','SISMEMBER','SMEMBERS','SMOVE','SORT','SPOP','SRANDMEMBER','SREM','SSCAN','STRLEN','SUBSTR','SUNION','SUNIONSTORE','TTL','TYPE','UNLINK','ZADD','ZCARD','ZCOUNT','ZINCRBY','ZINTERSTORE','ZLEXCOUNT','ZRANGE','ZRANGEBYLEX','ZRANGEBYSCORE','ZRANK','ZREM','ZREMRANGEBYLEX','ZREMRANGEBYSCORE','ZREVRANGE','ZREVRANGEBYSCORE','ZREVRANK','ZSCORE','ZUNIONSTORE'],
        'x-widget': 'searchableSelect',
      },
      key: { type: ['string', 'object'], title: 'Key', description: 'Redis Key（支持 {param} 模板或结构化绑定）', 'x-widget': 'binding' },
      args: { type: 'array', title: '参数列表', items: { type: ['string', 'object'], 'x-widget': 'binding' }, description: '命令参数列表（支持结构化绑定）' },
      script: { type: 'string', title: 'Lua 脚本', description: 'EVAL 命令专用 Lua 脚本' },
      keys: { type: 'array', title: 'EVAL KEYS', items: { type: ['string', 'object'], 'x-widget': 'binding' }, description: 'EVAL KEYS 参数' },
      commands: {
        type: 'array',
        title: 'Pipeline 命令',
        items: {
          type: 'object',
          properties: {
            command: {
              type: 'string',
              title: 'Redis 命令',
              enum: ['APPEND','BITCOUNT','BITOP','BITPOS','BLPOP','BRPOP','DBSIZE','DECR','DECRBY','DEL','ECHO','EVAL','EXISTS','EXPIRE','EXPIREAT','FLUSHALL','FLUSHDB','GET','GETBIT','GETRANGE','GETSET','HDEL','HEXISTS','HGET','HGETALL','HINCRBY','HKEYS','HLEN','HMGET','HMSET','HSCAN','HSET','HSETNX','HVALS','INCR','INCRBY','INCRBYFLOAT','KEYS','LINDEX','LINSERT','LLEN','LPOP','LPUSH','LPUSHX','LRANGE','LREM','LSET','LTRIM','MGET','MSET','MSETNX','PEXPIRE','PING','PIPELINE','PSETEX','PTTL','RPOP','RPUSH','RPUSHX','SADD','SCAN','SCARD','SDIFF','SDIFFSTORE','SET','SETBIT','SETEX','SETNX','SETRANGE','SINTER','SINTERSTORE','SISMEMBER','SMEMBERS','SMOVE','SORT','SPOP','SRANDMEMBER','SREM','SSCAN','STRLEN','SUBSTR','SUNION','SUNIONSTORE','TTL','TYPE','UNLINK','ZADD','ZCARD','ZCOUNT','ZINCRBY','ZINTERSTORE','ZLEXCOUNT','ZRANGE','ZRANGEBYLEX','ZRANGEBYSCORE','ZRANK','ZREM','ZREMRANGEBYLEX','ZREMRANGEBYSCORE','ZREVRANGE','ZREVRANGEBYSCORE','ZREVRANK','ZSCORE','ZUNIONSTORE'],
              'x-widget': 'searchableSelect',
            },
            key: { type: ['string', 'object'], title: 'Key', 'x-widget': 'binding' },
            args: { type: 'array', title: '参数', items: { type: ['string', 'object'], 'x-widget': 'binding' } },
          },
        },
        description: 'PIPELINE 命令列表',
      },
    },
  },
  // ── 手写 SQL DB 函数（通用执行器）─────────────────────────────────
  'builtin:dbExecute': {
    type: 'object',
    title: '手写 SQL 执行',
    required: ['sql'],
    properties: {
      sql: {
        type: 'string',
        title: 'SQL 语句',
        description: '支持 :paramName 命名参数、${template} 表名模板，可执行 SELECT/INSERT/UPDATE/DELETE/DDL 等任意 SQL',
        'x-widget': 'dbSqlEditor',
        'x-i18n': {
          zh: { description: 'SQL 语句，支持 :paramName 命名参数和 ${template} 表名模板，可执行 SELECT/INSERT/UPDATE/DELETE/DDL 等任意 SQL' },
          en: { description: 'SQL statement, supports :paramName named parameters and ${template} table name templates for SELECT/INSERT/UPDATE/DELETE/DDL' },
        },
      },
      params: {
        type: 'object',
        title: 'SQL 参数',
        description: '命名参数与模板变量绑定值 {paramName: value}，支持字面量或 {$ref} 引用上游节点输出',
        additionalProperties: true,
        'x-i18n': {
          zh: { description: 'SQL 参数绑定：键为 :paramName 参数名，值为字面量或 {$ref} 引用上游节点输出字段' },
          en: { description: 'SQL parameter bindings: key is :paramName, value is literal or {$ref} referencing upstream node output' },
        },
      },
      dataSource: {
        type: 'string',
        title: '数据源',
        default: 'default',
        description: '多数据源场景使用，如 default / order / user 等',
        'x-i18n': {
          zh: { description: '数据源名称，多数据源场景使用，如 default / order / user' },
          en: { description: 'Data source name, used in multi-datasource scenarios, e.g. default / order / user' },
        },
      },
      resultType: {
        type: 'string',
        title: '返回类型',
        enum: ['list', 'one', 'count'],
        default: 'list',
        description: 'SELECT/WITH 等查询语句的返回类型',
        'x-i18n': {
          zh: { description: '查询结果类型（仅 SELECT/WITH 生效）：list 返回列表、one 返回单条、count 返回行数' },
          en: { description: 'Query result type (SELECT/WITH only): list returns array, one returns single row, count returns row count' },
        },
      },
      limit: {
        type: 'integer',
        title: '最大行数',
        default: 1000,
        description: '查询语句最大返回行数，默认 1000；<=0 表示不限',
        'x-i18n': {
          zh: { description: '查询最大返回行数，默认 1000；<=0 表示不限' },
          en: { description: 'Max rows to return for queries, default 1000; <=0 means unlimited' },
        },
      },
      compensateSql: {
        type: 'string',
        title: '补偿 SQL',
        description: 'Saga 补偿时执行的 SQL',
        'x-widget': 'dbSqlEditor',
        'x-i18n': {
          zh: { description: 'Saga 补偿 SQL（写操作失败时执行的逆操作 SQL）' },
          en: { description: 'Saga compensation SQL (reverse operation SQL executed on write failure)' },
        },
      },
      compensateFunctionRef: {
        type: 'string',
        title: '补偿函数',
        description: 'Saga 补偿函数引用（与 compensateSql 二选一）',
        'x-i18n': {
          zh: { description: 'Saga 补偿函数引用（与 compensateSql 二选一）' },
          en: { description: 'Saga compensation function reference (mutually exclusive with compensateSql)' },
        },
      },
    },
  },
};
