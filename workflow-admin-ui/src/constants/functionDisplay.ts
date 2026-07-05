import type { FunctionDefinition } from '@/types/function';

/**
 * 函数能力分组元信息
 * 用于函数管理列表的卡片分组展示，替代原有按 domain 分类导致的“每类只有 1 个函数”问题
 */
export interface FunctionGroupMeta {
  key: string;
  label: string;
  icon: string;
  color: string;
  description?: string;
}

export const FUNCTION_GROUPS: FunctionGroupMeta[] = [
  {
    key: 'flow-control',
    label: '流程控制',
    icon: '🔀',
    color: '#06b6d4',
    description: '条件、过滤、并行、循环、路由、等待',
  },
  {
    key: 'data-access',
    label: '数据访问',
    icon: '🗄️',
    color: '#3b82f6',
    description: '数据库、缓存、Redis、HTTP、MQ',
  },
  {
    key: 'data-processing',
    label: '数据处理',
    icon: '🔄',
    color: '#8b5cf6',
    description: 'JSON、类型转换、分页',
  },
  {
    key: 'validation',
    label: '数据校验',
    icon: '✅',
    color: '#10b981',
    description: '入参与动态校验',
  },
  {
    key: 'response',
    label: '响应处理',
    icon: '📦',
    color: '#f59e0b',
    description: '响应与错误封装',
  },
  {
    key: 'script',
    label: '脚本执行',
    icon: '📝',
    color: '#ec4899',
    description: 'Groovy 与用户脚本',
  },
  {
    key: 'custom',
    label: '自定义函数',
    icon: '🧩',
    color: '#64748b',
    description: '应用自定义逻辑',
  },
  {
    key: 'external',
    label: '外部服务',
    icon: '🔌',
    color: '#fa8c16',
    description: 'HTTP/RPC 等外部调用',
  },
  {
    key: 'other',
    label: '其他',
    icon: '📦',
    color: '#8c8c8c',
    description: '未分类函数',
  },
];

export const FUNCTION_GROUP_ORDER = FUNCTION_GROUPS.map((g) => g.key);

export const FUNCTION_GROUP_MAP = Object.fromEntries(
  FUNCTION_GROUPS.map((g) => [g.key, g]),
) as Record<string, FunctionGroupMeta>;

/**
 * 内置函数友好名称映射
 * 解决函数列表直接展示英文 code（如 builtin:cacheGet）不够直观的问题
 */
export const BUILTIN_DISPLAY_NAMES: Record<string, string> = {
  'builtin:cacheGet': '缓存读取',
  'builtin:cacheSet': '缓存写入',
  'builtin:conditionBranch': '条件分支',
  'builtin:filter': '条件过滤',
  'builtin:dbExecute': '数据库执行',
  'builtin:errorWrapper': '错误封装',
  'builtin:fromJson': 'JSON 解析',
  'builtin:httpCall': 'HTTP 请求',
  'builtin:jsonExtract': 'JSON 提取',
  'builtin:jsonTransform': 'JSON 转换',
  'builtin:loopAggregator': '循环聚合',
  'builtin:mqPublish': 'MQ 发送',
  'builtin:paginate': '分页处理',
  'builtin:paramValidate': '参数校验',
  'builtin:responseWrapper': '响应封装',
  'builtin:toJson': 'JSON 序列化',
  'builtin:convert': '类型转换',
  'builtin:redisCommand': 'Redis 命令',
  'builtin:groovyScript': 'Groovy 脚本',
  'builtin:waitForSignal': '等待信号',
};

/**
 * 内置函数名 → 能力分组映射
 * 不依赖 nodeType，因为后端对很多内置函数统一映射为 CUSTOM，无法有效分组
 */
const BUILTIN_NAME_TO_GROUP: Record<string, string> = {
  'builtin:conditionBranch': 'flow-control',
  'builtin:filter': 'flow-control',
  'builtin:parallel': 'flow-control',
  'builtin:eipRouter': 'flow-control',
  'builtin:subWorkflow': 'flow-control',
  'builtin:loopAggregator': 'flow-control',
  'builtin:waitForSignal': 'flow-control',

  'builtin:dbExecute': 'data-access',
  'builtin:redisCommand': 'data-access',
  'builtin:httpCall': 'data-access',
  'builtin:mqPublish': 'data-access',
  'builtin:cacheGet': 'data-access',
  'builtin:cacheSet': 'data-access',

  'builtin:toJson': 'data-processing',
  'builtin:fromJson': 'data-processing',
  'builtin:jsonExtract': 'data-processing',
  'builtin:jsonTransform': 'data-processing',
  'builtin:convert': 'data-processing',
  'builtin:paginate': 'data-processing',

  'builtin:paramValidate': 'validation',
  'builtin:dynamicValidate': 'validation',

  'builtin:responseWrapper': 'response',
  'builtin:errorWrapper': 'response',

  'builtin:groovyScript': 'script',
};

/**
 * 将 camelCase / PascalCase 字符串拆分为可读单词
 * 例如：getUserOrder -> Get User Order
 */
function splitCamelCase(str: string): string {
  return str
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();
}

/**
 * 获取函数的友好展示名称
 * 1. 内置函数优先走中文映射表
 * 2. 尝试 config.displayName / config.title（预留后端扩展）
 * 3. 对 code 名称做美化
 */
export function getFunctionDisplayName(fn: FunctionDefinition): string {
  const { name, config } = fn;
  if (!name) return '未命名函数';

  if (BUILTIN_DISPLAY_NAMES[name]) {
    return BUILTIN_DISPLAY_NAMES[name];
  }

  const displayName = config?.displayName || config?.title;
  if (typeof displayName === 'string' && displayName.trim()) {
    return displayName.trim();
  }

  const parts = name.split(':');
  const lastPart = parts[parts.length - 1] || name;
  const readable = splitCamelCase(lastPart);
  return readable
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

/**
 * 获取函数的展示分组 key
 */
export function getFunctionGroupKey(fn: FunctionDefinition): string {
  const { category, name } = fn;

  if (category === 'EXTERNAL') return 'external';
  if (category === 'CUSTOM') return 'custom';
  if (category === 'SCRIPT') return 'script';

  if (name && BUILTIN_NAME_TO_GROUP[name]) {
    return BUILTIN_NAME_TO_GROUP[name];
  }

  return 'other';
}

/**
 * 获取函数的短描述
 * 超过指定长度时截断，调用方自行通过 Tooltip 展示完整内容
 */
export function getFunctionDescription(fn: FunctionDefinition, maxLength = 80): string {
  const desc = (fn.config?.description as string) || '-';
  if (desc.length <= maxLength) return desc;
  return `${desc.slice(0, maxLength)}…`;
}

/**
 * 获取函数完整描述（用于 Tooltip）
 */
export function getFunctionFullDescription(fn: FunctionDefinition): string {
  return (fn.config?.description as string) || '暂无描述';
}
