/**
 * 条件函数（conditionBranch / filter / conditionalNexts）测试辅助工具。
 *
 * - operator 中文描述
 * - 从 JSON Schema 生成示例测试数据
 * - 从 JSON Schema 提取字段路径（用于 field 自动补全）
 */

/**
 * 结构化规则操作符 → 中文描述。
 *
 * 注：neq / notIn / isNotNull 等反向操作符已移除，
 * 通过对应基础操作符 + negate=true 实现相同效果。
 * 后端 RuleEvaluator 仍兼容旧数据中的反向操作符。
 */
export const OPERATOR_LABELS: Record<string, string> = {
  eq: '等于',
  gt: '大于',
  gte: '大于等于',
  lt: '小于',
  lte: '小于等于',
  in: '在列表中',
  contains: '包含',
  isNull: '为空',
  isEmpty: '为空集合',
  sizeEq: '长度等于',
  sizeGt: '长度大于',
  sizeGte: '长度大于等于',
  sizeLt: '长度小于',
  sizeLte: '长度小于等于',
  startsWith: '以...开头',
  endsWith: '以...结尾',
  regex: '正则匹配',
};

/** 所有已知条件函数标识 */
export const CONDITION_FUNCTION_IDS = new Set([
  'builtin:conditionBranch',
  'builtin:filter',
  'builtin:conditionalNexts',
]);

/** 判断是否为条件控制类函数 */
export function isConditionFunction(functionId?: string): boolean {
  if (!functionId) return false;
  return CONDITION_FUNCTION_IDS.has(functionId);
}

/**
 * 为 schema 中所有名为 operator 且枚举包含已知操作符的字段添加 enumNames。
 *
 * 返回新的 schema 对象，不修改原对象。
 */
export function applyOperatorLabels(schema: Record<string, any>): Record<string, any> {
  if (!schema || typeof schema !== 'object') return schema;
  const result: Record<string, any> = { ...schema };

  // 当前节点是 operator 字段
  if (
    result.type === 'string' &&
    Array.isArray(result.enum) &&
    result.enum.some((v: string) => OPERATOR_LABELS[v])
  ) {
    result.enumNames = result.enum.map((v: string) => {
      const label = OPERATOR_LABELS[v];
      return label ? `${v} (${label})` : v;
    });
  }

  // 递归 properties
  if (result.properties && typeof result.properties === 'object') {
    const newProps: Record<string, any> = {};
    for (const [key, prop] of Object.entries(result.properties)) {
      newProps[key] = applyOperatorLabels(prop as Record<string, any>);
    }
    result.properties = newProps;
  }

  // 递归 items
  if (result.items && typeof result.items === 'object') {
    result.items = applyOperatorLabels(result.items as Record<string, any>);
  }

  return result;
}

/**
 * 根据 JSON Schema 生成一份示例数据（Map 结构）。
 *
 * 支持 object / array / string / number / integer / boolean / enum，
 * 并根据 schema 的 format / minimum / maximum / minLength / maxLength
 * 生成更贴近真实使用场景的随机值。
 */
export function generateSampleFromSchema(schema: Record<string, any>): any {
  if (!schema || typeof schema !== 'object') return null;

  if ('default' in schema) return schema.default;
  if (Array.isArray(schema.enum) && schema.enum.length > 0) {
    return schema.enum[Math.floor(Math.random() * schema.enum.length)];
  }

  const types = Array.isArray(schema.type)
    ? schema.type
    : schema.type
    ? [schema.type]
    : [];

  if (types.includes('object') && schema.properties) {
    const sample: Record<string, any> = {};
    for (const [key, prop] of Object.entries(schema.properties)) {
      sample[key] = generateSampleFromSchema(prop as Record<string, any>);
    }
    return sample;
  }

  if (types.includes('array') && schema.items) {
    return [generateSampleFromSchema(schema.items as Record<string, any>)];
  }

  if (types.includes('string')) return generateStringValue(schema);
  if (types.includes('number') || types.includes('integer')) return generateNumberValue(schema);
  if (types.includes('boolean')) return Math.random() >= 0.5;

  return null;
}

function generateNumberValue(schema: Record<string, any>): number {
  const minimum = Number.isFinite(schema.minimum) ? schema.minimum : 0;
  const maximum = Number.isFinite(schema.maximum) ? schema.maximum : minimum + 100;
  const types = Array.isArray(schema.type) ? schema.type : schema.type ? [schema.type] : [];
  const isInteger = types.includes('integer') || (types.includes('number') && Number.isInteger(schema.minimum));

  if (maximum === minimum) return Number(minimum);

  const span = maximum - minimum;
  const value = minimum + Math.random() * span;
  return isInteger ? Math.floor(value) : Number(value.toFixed(2));
}

function generateStringValue(schema: Record<string, any>): string {
  const minLength = Number.isFinite(schema.minLength) ? schema.minLength : 4;
  const maxLength = Number.isFinite(schema.maxLength) ? schema.maxLength : Math.max(minLength, 12);
  const length = Math.floor(Math.random() * (Math.max(minLength, maxLength) - minLength + 1)) + minLength;

  switch (schema.format) {
    case 'email':
      return `user${Math.floor(Math.random() * 9000) + 1000}@example.com`;
    case 'date-time':
      return new Date(Date.now() + Math.floor(Math.random() * 1000000000)).toISOString();
    case 'date':
      return new Date(Date.now() + Math.floor(Math.random() * 1000000000)).toISOString().split('T')[0];
    case 'time':
      return '12:34:56';
    case 'uri':
    case 'url':
      return `https://example.com/${randomLabel(length)}`;
    case 'uuid':
      return '550e8400-e29b-41d4-a716-446655440000';
    case 'hostname':
      return `example-${Math.floor(Math.random() * 1000) + 1}.com`;
    case 'ipv4':
      return `192.168.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
    case 'ipv6':
      return '2001:db8::1';
    default:
      return randomLabel(length);
  }
}

function randomLabel(length: number): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = 'sample';
  for (let i = 0; i < Math.max(0, length - 6); i += 1) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

/**
 * 不需要 value 的操作符（空值判断/空集合判断）。
 *
 * 注：反向语义操作符（如 isNotNull / notIn / neq）不再暴露给 UI，
 * 用户通过 negate=true + 基础操作符实现等价效果。后端仍然兼容旧数据。
 */
export const VALUE_LESS_OPERATORS = new Set(['isNull', 'isEmpty']);

/** 长度（size）系列操作符 —— 需要数值类型 value */
export const SIZE_OPERATORS = new Set(['sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte']);

/** 字段类型（JSON Schema 原始类型归一化后的子集） */
export type FieldType =
  | 'string'
  | 'number'
  | 'integer'
  | 'boolean'
  | 'object'
  | 'array'
  | 'null'
  | 'unknown';

/**
 * 各字段类型 → 允许选择的操作符白名单。
 *
 * 设计原则：
 *  - string：支持等于、大小比较、列表包含、子串、前缀后缀、正则、空值/空串、长度。
 *  - number/integer：仅支持数值比较（大小、等于、in）、空值。注意：不支持 contains 等字符串操作。
 *  - boolean：仅支持等于（true/false）、空值。
 *  - array：支持空值、集合为空、长度比较、元素包含（contains）、in（in 语义上判断元素是否 in 列表，其实应避免，但保留方便）。
 *  - object：支持空值、集合为空（无属性）、长度比较（属性数）。
 *  - null/unknown：退化为通用基础操作符（仅能做空值、等于、in 等最保守的判断）。
 */
const OPERATORS_BY_TYPE: Record<FieldType, string[]> = {
  string: [
    'eq',
    'gt', 'gte', 'lt', 'lte',
    'in',
    'contains',
    'startsWith', 'endsWith', 'regex',
    'isNull', 'isEmpty',
    'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte',
  ],
  number: [
    'eq',
    'gt', 'gte', 'lt', 'lte',
    'in',
    'isNull',
  ],
  integer: [
    'eq',
    'gt', 'gte', 'lt', 'lte',
    'in',
    'isNull',
  ],
  boolean: ['eq', 'isNull'],
  array: [
    'isNull', 'isEmpty',
    'sizeGte', 'sizeGt', 'sizeLte', 'sizeLt', 'sizeEq',
    'contains',
    'in',
  ],
  object: [
    'isNull', 'isEmpty',
    'sizeGte', 'sizeGt', 'sizeLte', 'sizeLt', 'sizeEq',
  ],
  null: ['isNull', 'isEmpty'],
  unknown: [
    'eq',
    'gt', 'gte', 'lt', 'lte',
    'in',
    'contains',
    'isNull', 'isEmpty',
    'startsWith', 'endsWith', 'regex',
    'sizeEq', 'sizeGt', 'sizeGte', 'sizeLt', 'sizeLte',
  ],
};

/**
 * 根据字段类型返回该字段允许的操作符集合。
 * 如果传入的 fieldType 为 undefined / null，则返回"通用全集（unknown）"保证不缺选项。
 */
export function getOperatorsForFieldType(fieldType?: FieldType | null | string): string[] {
  if (!fieldType) return OPERATORS_BY_TYPE.unknown;
  return OPERATORS_BY_TYPE[fieldType as FieldType] || OPERATORS_BY_TYPE.unknown;
}

/**
 * 从 JSON Schema 中提取所有可用字段路径（含中间节点）。
 *
 * 支持 object 嵌套和 array items 嵌套，同时包含中间节点路径：
 * - { properties: { user: { properties: { name: {} } } } } → ['user', 'user.name']
 * - { properties: { items: { type: 'array', items: { properties: { name: {} } } } } }
 *   → ['items', 'items.productId', 'items.name', ...]
 */
export function extractSchemaFieldPaths(schema: Record<string, any>, prefix = ''): string[] {
  if (!schema || typeof schema !== 'object') return [];

  const props = schema.properties;
  if (!props || typeof props !== 'object') return prefix ? [prefix] : [];

  const paths: string[] = [];
  for (const [key, prop] of Object.entries(props as Record<string, any>)) {
    const path = prefix ? `${prefix}.${key}` : key;

    // 嵌套 object：包含自身路径 + 递归子字段
    if (prop?.properties && typeof prop.properties === 'object') {
      paths.push(path); // 包含自身（可用于 isNull 等判断）
      const childPaths = extractSchemaFieldPaths(prop, path);
      paths.push(...childPaths);
      continue;
    }

    // array 类型：包含自身路径 + 深入 items 提取嵌套字段路径
    const propType = Array.isArray(prop?.type) ? prop.type : (prop?.type ? [prop.type] : []);
    if (propType.includes('array') && prop?.items && typeof prop.items === 'object') {
      paths.push(path); // 包含自身（如 items 可用于 isEmpty / size 判断）
      const itemPaths = extractSchemaFieldPaths(prop.items, path);
      paths.push(...itemPaths);
      continue;
    }

    // 叶子字段
    paths.push(path);
  }
  return paths;
}

/**
 * 从 JSON Schema 提取"字段路径 → 字段类型"映射，用于操作符白名单过滤。
 *
 * 类型解析规则：
 *  - JSON Schema `type: string` → 'string'
 *  - `type: number` → 'number'
 *  - `type: integer` → 'integer'
 *  - `type: boolean` → 'boolean'
 *  - `type: null` → 'null'
 *  - `type: object` / 存在 `properties` → 'object'
 *  - `type: array` / 存在 `items` → 'array'
 *  - 当 `type` 为数组（JSON Schema 组合类型）时，取第一个非 null 的有意义类型
 *  - 无法识别时返回 'unknown'
 */
export function extractSchemaFieldTypes(
  schema: Record<string, any>,
  prefix = '',
  out: Record<string, FieldType> = {},
): Record<string, FieldType> {
  if (!schema || typeof schema !== 'object') return out;

  const props = schema.properties;
  if (!props || typeof props !== 'object') {
    if (prefix) {
      out[prefix] = resolveSingleType(schema);
    }
    return out;
  }

  for (const [key, prop] of Object.entries(props as Record<string, any>)) {
    const path = prefix ? `${prefix}.${key}` : key;
    const resolved = resolveSingleType(prop);
    out[path] = resolved;

    if (prop?.properties && typeof prop.properties === 'object') {
      extractSchemaFieldTypes(prop, path, out);
      continue;
    }

    const propType = Array.isArray(prop?.type) ? prop.type : (prop?.type ? [prop.type] : []);
    if (propType.includes('array') && prop?.items && typeof prop.items === 'object') {
      extractSchemaFieldTypes(prop.items, path, out);
      continue;
    }
  }
  return out;
}

/** 将任意 JSON Schema 的 type 归一到 FieldType 枚举 */
export function resolveSingleType(schema: Record<string, any> | null | undefined): FieldType {
  if (!schema || typeof schema !== 'object') return 'unknown';

  const rawTypes: any[] = Array.isArray(schema.type) ? schema.type : schema.type ? [schema.type] : [];

  // 优先处理 object/array 标识
  if (schema.properties || rawTypes.includes('object')) return 'object';
  if (schema.items || rawTypes.includes('array')) return 'array';

  const meaningful = rawTypes.find((t) => t !== 'null');
  if (!meaningful) {
    if (rawTypes.includes('null')) return 'null';
    return 'unknown';
  }

  switch (meaningful) {
    case 'string':
    case 'number':
    case 'integer':
    case 'boolean':
    case 'null':
      return meaningful as FieldType;
    default:
      return 'unknown';
  }
}

/**
 * 单个字段的完整 schema 元信息（用于 hover 提示框展示）。
 */
export interface FieldSchemaMeta {
  path: string;
  name: string;
  title?: string;
  description?: string;
  type: FieldType;
  format?: string;
  required: boolean;
  itemType?: FieldType;
  /** 约束摘要（如 minLength/maxLength/minimum/maximum/enum 等），方便展示 */
  constraints: string[];
  /** 原始 schema 对象（用于高级场景） */
  rawSchema: Record<string, any>;
}

/**
 * 从 JSON Schema 中提取所有字段路径的完整元信息。
 *
 * 输出：字段路径 → FieldSchemaMeta 映射，供 UI 下拉框在 hover 时展示完整 schema 信息。
 *
 * 支持嵌套 object / array items，与 extractSchemaFieldPaths 遍历路径保持一致。
 */
export function extractSchemaFieldMeta(
  schema: Record<string, any>,
  prefix = '',
  requiredFields: string[] = [],
  out: Record<string, FieldSchemaMeta> = {},
): Record<string, FieldSchemaMeta> {
  if (!schema || typeof schema !== 'object') return out;

  const props = schema.properties;
  if (!props || typeof props !== 'object') {
    if (prefix) {
      out[prefix] = buildFieldMeta(prefix, prefix.split('.').pop()!, schema, requiredFields.includes(prefix.split('.').pop()!));
    }
    return out;
  }

  const currentRequired: string[] = Array.isArray(schema.required) ? schema.required : [];

  for (const [key, prop] of Object.entries(props as Record<string, any>)) {
    const path = prefix ? `${prefix}.${key}` : key;
    const required = currentRequired.includes(key);
    out[path] = buildFieldMeta(path, key, prop, required);

    if (prop?.properties && typeof prop.properties === 'object') {
      extractSchemaFieldMeta(prop, path, Array.isArray(prop.required) ? prop.required : [], out);
      continue;
    }

    const propType = Array.isArray(prop?.type) ? prop.type : (prop?.type ? [prop.type] : []);
    if (propType.includes('array') && prop?.items && typeof prop.items === 'object') {
      const itemMeta = buildFieldMeta(path, key, prop.items, false);
      if (out[path]) {
        out[path] = { ...out[path], itemType: itemMeta.type };
      }
      extractSchemaFieldMeta(prop.items, path, Array.isArray(prop.items.required) ? prop.items.required : [], out);
      continue;
    }
  }
  return out;
}

/** 构造单个字段的 FieldSchemaMeta，解析 title/description/format/enum/min/max 等关键属性 */
function buildFieldMeta(
  path: string,
  name: string,
  schema: Record<string, any>,
  required: boolean,
): FieldSchemaMeta {
  const type = resolveSingleType(schema);
  const constraints: string[] = [];

  if (schema.enum && Array.isArray(schema.enum) && schema.enum.length > 0) {
    const preview = schema.enum.slice(0, 5).join(', ');
    constraints.push(`枚举值: ${preview}${schema.enum.length > 5 ? ` 等 ${schema.enum.length} 个` : ''}`);
  }

  if (type === 'string') {
    if (schema.minLength != null) constraints.push(`最短 ${schema.minLength} 字符`);
    if (schema.maxLength != null) constraints.push(`最长 ${schema.maxLength} 字符`);
    if (schema.pattern) constraints.push(`正则匹配: ${String(schema.pattern).slice(0, 40)}`);
  }

  if (type === 'number' || type === 'integer') {
    if (schema.minimum != null) constraints.push(`最小值: ${schema.minimum}`);
    if (schema.maximum != null) constraints.push(`最大值: ${schema.maximum}`);
    if (schema.exclusiveMinimum != null) constraints.push(`严格大于: ${schema.exclusiveMinimum}`);
    if (schema.exclusiveMaximum != null) constraints.push(`严格小于: ${schema.exclusiveMaximum}`);
    if (schema.multipleOf != null) constraints.push(`${schema.multipleOf} 的倍数`);
  }

  if (type === 'array') {
    if (schema.minItems != null) constraints.push(`最少 ${schema.minItems} 项`);
    if (schema.maxItems != null) constraints.push(`最多 ${schema.maxItems} 项`);
    if (schema.uniqueItems) constraints.push(`元素唯一`);
  }

  if (schema.default != null) {
    const defStr = String(schema.default);
    constraints.push(`默认值: ${defStr.length > 30 ? `${defStr.slice(0, 30)}…` : defStr}`);
  }

  let itemType: FieldType | undefined;
  if (type === 'array' && schema.items) {
    itemType = resolveSingleType(schema.items);
  }

  return {
    path,
    name,
    title: schema.title,
    description: schema.description,
    type,
    format: schema.format,
    required,
    itemType,
    constraints,
    rawSchema: schema,
  };
}
