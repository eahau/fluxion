/**
 * English message resources
 * Auto-registered by Umi plugin-locale (src/locales/{lang}.ts default export).
 * Keys MUST match zh-CN.ts one-to-one; missing keys fall back to the id, no runtime break.
 */

const messages = {
  // ── Function capability groups (functionDisplay.ts FUNCTION_GROUPS) ──
  'functionGroup.flowControl.label': 'Flow Control',
  'functionGroup.flowControl.description': 'Condition, filter, parallel, loop, routing, wait',
  'functionGroup.dataAccess.label': 'Data Access',
  'functionGroup.dataAccess.description': 'Database, cache, Redis, HTTP, MQ',
  'functionGroup.dataProcessing.label': 'Data Processing',
  'functionGroup.dataProcessing.description': 'JSON, type conversion, pagination',
  'functionGroup.validation.label': 'Validation',
  'functionGroup.validation.description': 'Input and dynamic validation',
  'functionGroup.response.label': 'Response',
  'functionGroup.response.description': 'Response and error wrapping',
  'functionGroup.script.label': 'Script',
  'functionGroup.script.description': 'Groovy and user scripts',
  'functionGroup.custom.label': 'Custom',
  'functionGroup.custom.description': 'Application custom logic',
  'functionGroup.external.label': 'External',
  'functionGroup.external.description': 'HTTP/RPC and other external calls',
  'functionGroup.other.label': 'Other',
  'functionGroup.other.description': 'Uncategorized functions',

  // ── Builtin function friendly names (functionDisplay.ts BUILTIN_DISPLAY_NAMES) ──
  'builtinFunction.cacheGet': 'Cache Read',
  'builtinFunction.cacheSet': 'Cache Write',
  'builtinFunction.conditionBranch': 'Condition Branch',
  'builtinFunction.filter': 'Filter',
  'builtinFunction.dbExecute': 'Database Execute',
  'builtinFunction.errorWrapper': 'Error Wrapper',
  'builtinFunction.fromJson': 'JSON Parse',
  'builtinFunction.httpCall': 'HTTP Request',
  'builtinFunction.jsonExtract': 'JSON Extract',
  'builtinFunction.jsonTransform': 'JSON Transform',
  'builtinFunction.loopAggregator': 'Loop Aggregator',
  'builtinFunction.mqPublish': 'MQ Publish',
  'builtinFunction.paginate': 'Paginate',
  'builtinFunction.paramValidate': 'Param Validate',
  'builtinFunction.responseWrapper': 'Response Wrapper',
  'builtinFunction.toJson': 'JSON Serialize',
  'builtinFunction.convert': 'Convert',
  'builtinFunction.redisCommand': 'Redis Command',
  'builtinFunction.groovyScript': 'Groovy Script',
  'builtinFunction.waitForSignal': 'Wait For Signal',

  // ── Function display fallbacks ──
  'function.display.unnamed': 'Unnamed Function',
  'function.description.empty': 'No description',

  // ── Common placeholder / truncation ──
  'common.placeholder.dash': '-',
  'common.text.truncationSuffix': '…',

  // ── Function domains (domain.ts DOMAIN_META) ──
  'domain.all.label': 'All',
  'domain.db.label': 'Database',
  'domain.redis.label': 'Redis',
  'domain.cache.label': 'Cache',
  'domain.http.label': 'HTTP',
  'domain.mq.label': 'Message Queue',
  'domain.script.label': 'Script',
  'domain.common.label': 'Common',
  'domain.other.label': 'Other',

  // ── Node types (nodeTypes.ts NODE_TYPE_OPTIONS) ──
  'nodeType.PARAM_VALIDATE.label': 'Param Validate',
  'nodeType.DYNAMIC_VALIDATE.label': 'Dynamic Validate',
  'nodeType.DATA_QUERY.label': 'Data Query',
  'nodeType.DATA_TRANSFORM.label': 'Data Transform',
  'nodeType.ASSEMBLE_RESPONSE.label': 'Assemble Response',
  'nodeType.SCRIPT.label': 'Script',
  'nodeType.CUSTOM.label': 'Custom Node',
  'nodeType.CONDITION_BRANCH.label': 'Condition Branch',
  'nodeType.FILTER.label': 'Filter',
  'nodeType.PARALLEL.label': 'Parallel',
  'nodeType.SUB_WORKFLOW.label': 'Sub Workflow',
  'nodeType.EIP_ROUTER.label': 'EIP Router',
  'nodeType.LOOP.label': 'Loop',
  'nodeType.WAIT.label': 'Wait',

  // ── Node role groups (nodeTypes.ts NODE_ROLE_GROUPS) ──
  'nodeRole.CONTROLLER.label': 'Controller',
  'nodeRole.CONTROLLER.desc': 'Flow orchestration and routing control',
  'nodeRole.EXECUTOR.label': 'Executor',
  'nodeRole.EXECUTOR.desc': 'Business logic and data processing',

  // ── Protocol config (protocol.ts getProtocolConfig) ──
  'protocol.http.label': 'HTTP',
  'protocol.http.bindLabel': 'Request Path',
  'protocol.http.bindPlaceholder': '/api/example',
  'protocol.https.label': 'HTTPS',
  'protocol.https.bindLabel': 'Request Path',
  'protocol.https.bindPlaceholder': '/api/example',
  'protocol.grpc.label': 'gRPC',
  'protocol.grpc.bindLabel': 'Service Key (serviceKey)',
  'protocol.grpc.bindPlaceholder': 'com.example.UserService/GetUser',
  'protocol.dubbo.label': 'Dubbo',
  'protocol.dubbo.bindLabel': 'Service Key (serviceKey)',
  'protocol.dubbo.bindPlaceholder': 'com.example.UserService',
  'protocol.kafka.label': 'Kafka',
  'protocol.kafka.bindLabel': 'Bind Key (bindKey)',
  'protocol.kafka.bindPlaceholder': 'order.created',
  'protocol.unknown.label': 'Unknown',
  'protocol.default.bindLabel': 'Bind Key',
  'protocol.default.bindPlaceholder': 'Please enter bind key',

  // ── App fatal error (app.tsx) ──
  'app.fatalError.title': 'Fatal error during application startup',
  'app.fatalError.subTitleDev': 'Exception details are printed below. Fix the code and refresh; other pages/API services usually remain accessible.',
  'app.fatalError.subTitleProd': 'Please refresh the page to retry; if it still cannot open, contact the administrator.',
  'app.fatalError.refresh': 'Refresh',
  'app.fatalError.backHome': 'Back to Home',
};

export default messages;
