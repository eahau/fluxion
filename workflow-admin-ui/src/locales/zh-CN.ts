/**
 * 中文文案资源（默认语言）
 * 由 Umi plugin-locale 自动注册（src/locales/{lang}.ts 默认导出）。
 * key 必须与 en-US.ts 一一对应；缺失 key 时 formatMessage 回退显示 id，不影响运行。
 */

const messages = {
  // ── 函数能力分组（functionDisplay.ts FUNCTION_GROUPS）──
  'functionGroup.flowControl.label': '流程控制',
  'functionGroup.flowControl.description': '条件、过滤、并行、循环、路由、等待',
  'functionGroup.dataAccess.label': '数据访问',
  'functionGroup.dataAccess.description': '数据库、缓存、Redis、HTTP、MQ',
  'functionGroup.dataProcessing.label': '数据处理',
  'functionGroup.dataProcessing.description': 'JSON、类型转换、分页',
  'functionGroup.validation.label': '数据校验',
  'functionGroup.validation.description': '入参与动态校验',
  'functionGroup.response.label': '响应处理',
  'functionGroup.response.description': '响应与错误封装',
  'functionGroup.script.label': '脚本执行',
  'functionGroup.script.description': 'Groovy 与用户脚本',
  'functionGroup.custom.label': '自定义函数',
  'functionGroup.custom.description': '应用自定义逻辑',
  'functionGroup.external.label': '外部服务',
  'functionGroup.external.description': 'HTTP/RPC 等外部调用',
  'functionGroup.other.label': '其他',
  'functionGroup.other.description': '未分类函数',

  // ── 内置函数友好名（functionDisplay.ts BUILTIN_DISPLAY_NAMES）──
  'builtinFunction.cacheGet': '缓存读取',
  'builtinFunction.cacheSet': '缓存写入',
  'builtinFunction.conditionBranch': '条件分支',
  'builtinFunction.filter': '条件过滤',
  'builtinFunction.dbExecute': '数据库执行',
  'builtinFunction.errorWrapper': '错误封装',
  'builtinFunction.fromJson': 'JSON 解析',
  'builtinFunction.httpCall': 'HTTP 请求',
  'builtinFunction.jsonExtract': 'JSON 提取',
  'builtinFunction.jsonTransform': 'JSON 转换',
  'builtinFunction.loopAggregator': '循环聚合',
  'builtinFunction.mqPublish': 'MQ 发送',
  'builtinFunction.paginate': '分页处理',
  'builtinFunction.paramValidate': '参数校验',
  'builtinFunction.responseWrapper': '响应封装',
  'builtinFunction.toJson': 'JSON 序列化',
  'builtinFunction.convert': '类型转换',
  'builtinFunction.redisCommand': 'Redis 命令',
  'builtinFunction.groovyScript': 'Groovy 脚本',
  'builtinFunction.waitForSignal': '等待信号',

  // ── 函数展示兜底文案 ──
  'function.display.unnamed': '未命名函数',
  'function.description.empty': '暂无描述',

  // ── 通用占位 / 截断 ──
  'common.placeholder.dash': '-',
  'common.text.truncationSuffix': '…',

  // ── 函数领域（domain.ts DOMAIN_META）──
  'domain.all.label': '全部',
  'domain.db.label': '数据库',
  'domain.redis.label': 'Redis',
  'domain.cache.label': '缓存',
  'domain.http.label': 'HTTP',
  'domain.mq.label': '消息队列',
  'domain.script.label': '脚本',
  'domain.common.label': '通用',
  'domain.other.label': '其他',

  // ── 节点类型（nodeTypes.ts NODE_TYPE_OPTIONS）──
  'nodeType.PARAM_VALIDATE.label': '入参校验',
  'nodeType.DYNAMIC_VALIDATE.label': '动态校验',
  'nodeType.DATA_QUERY.label': '数据查询',
  'nodeType.DATA_TRANSFORM.label': '数据转换',
  'nodeType.ASSEMBLE_RESPONSE.label': '组装响应',
  'nodeType.SCRIPT.label': '脚本执行',
  'nodeType.CUSTOM.label': '自定义节点',
  'nodeType.CONDITION_BRANCH.label': '条件分支',
  'nodeType.FILTER.label': '条件过滤',
  'nodeType.PARALLEL.label': '并行执行',
  'nodeType.SUB_WORKFLOW.label': '子工作流',
  'nodeType.EIP_ROUTER.label': 'EIP 路由',
  'nodeType.LOOP.label': '循环节点',
  'nodeType.WAIT.label': '等待节点',

  // ── 节点角色分组（nodeTypes.ts NODE_ROLE_GROUPS）──
  'nodeRole.CONTROLLER.label': '控制器',
  'nodeRole.CONTROLLER.desc': '流程编排与路由控制',
  'nodeRole.EXECUTOR.label': '执行器',
  'nodeRole.EXECUTOR.desc': '业务逻辑与数据处理',

  // ── 协议配置（protocol.ts getProtocolConfig）──
  'protocol.http.label': 'HTTP',
  'protocol.http.bindLabel': '请求路径',
  'protocol.http.bindPlaceholder': '/api/example',
  'protocol.https.label': 'HTTPS',
  'protocol.https.bindLabel': '请求路径',
  'protocol.https.bindPlaceholder': '/api/example',
  'protocol.grpc.label': 'gRPC',
  'protocol.grpc.bindLabel': '服务键 (serviceKey)',
  'protocol.grpc.bindPlaceholder': 'com.example.UserService/GetUser',
  'protocol.dubbo.label': 'Dubbo',
  'protocol.dubbo.bindLabel': '服务键 (serviceKey)',
  'protocol.dubbo.bindPlaceholder': 'com.example.UserService',
  'protocol.kafka.label': 'Kafka',
  'protocol.kafka.bindLabel': '绑定键 (bindKey)',
  'protocol.kafka.bindPlaceholder': 'order.created',
  'protocol.unknown.label': '未知',
  'protocol.default.bindLabel': '绑定键',
  'protocol.default.bindPlaceholder': '请输入绑定键',

  // ── 应用启动致命错误（app.tsx）──
  'app.fatalError.title': '应用启动阶段出现致命错误',
  'app.fatalError.subTitleDev': '下方已打印异常详情。修复代码后刷新即可；其余页面/接口服务通常仍可正常访问。',
  'app.fatalError.subTitleProd': '请刷新页面重试；若仍无法打开请联系管理员。',
  'app.fatalError.refresh': '刷新页面',
  'app.fatalError.backHome': '回到首页',
};

export default messages;
