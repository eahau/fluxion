/**
 * Schema 兼容性兜底工具
 *
 * 遵循「单份 Schema、只增不删、无多版本文件、靠运行逻辑兼容老客户端」的原则，
 * 所有接口响应解析必须通过此工具处理，保证：
 * 1. 所有字段使用可选链 / 空值兜底，新字段为 null 不报错
 * 2. 未知枚举值统一展示「未知」，不白屏
 * 3. 类型不匹配时做安全转换，不阻断页面渲染
 * 4. 废弃字段解析保留，页面逐步隐藏
 */

// ─── 类型容错 ──────────────────────────────────────────

/** 安全转换为数字，失败返回 undefined */
export function safeNumber(value: unknown, fallback?: number): number | undefined {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'number') return value;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') return fallback;
    const num = Number(trimmed);
    return Number.isNaN(num) ? fallback : num;
  }
  if (typeof value === 'boolean') return value ? 1 : 0;
  return fallback;
}

/** 安全转换为字符串，失败返回 undefined */
export function safeString(value: unknown, fallback?: string): string | undefined {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (typeof value === 'object') {
    try { return JSON.stringify(value); } catch { return fallback; }
  }
  return fallback;
}

/** 安全转换为布尔值，失败返回 undefined */
export function safeBoolean(value: unknown, fallback?: boolean): boolean | undefined {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') {
    const lower = value.trim().toLowerCase();
    if (['true', '1', 'yes', 'on'].includes(lower)) return true;
    if (['false', '0', 'no', 'off'].includes(lower)) return false;
    return fallback;
  }
  return fallback;
}

/** 安全转换为数组，失败返回空数组 */
export function safeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[];
  if (value === null || value === undefined) return [];
  return [];
}

/** 安全转换为对象，失败返回空对象 */
export function safeObject(value: unknown): Record<string, unknown> {
  if (value !== null && value !== undefined && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

// ─── 枚举兼容 ──────────────────────────────────────────

/**
 * 未知枚举值兼容展示。
 * 后端返回未知枚举值时，统一展示「未知」，不白屏。
 */
export function safeEnumLabel<T extends string>(
  value: string | null | undefined,
  enumMap: Record<string, T>,
  fallbackLabel: string = '未知',
): T | string {
  if (!value) return fallbackLabel;
  return enumMap[value] ?? fallbackLabel;
}

/**
 * 多枚举值兼容（逗号分隔字符串转数组）。
 * 后端可能返回逗号分隔的枚举字符串，统一转为数组。
 */
export function safeEnumList(value: string | null | undefined): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);
}

// ─── 安全嵌套读取 ──────────────────────────────────────

/**
 * 安全读取嵌套对象的属性值（兼容 null 中间节点）。
 * 替代 `obj?.a?.b?.c`，适合在非 TS 严格模式或模板中调用。
 */
export function safeGet<T>(obj: unknown, path: string, fallback?: T): T | undefined {
  if (!obj || !path) return fallback;
  const keys = path.split('.');
  let current: unknown = obj;
  for (const key of keys) {
    if (current === null || current === undefined) return fallback;
    if (typeof current !== 'object') return fallback;
    current = (current as Record<string, unknown>)[key];
  }
  return (current as T) ?? fallback;
}

// ─── 兼容的响应包装 ────────────────────────────────────

/**
 * 包装 API 响应，对所有字段做兼容兜底。
 * 适用于已知可能返回 null/undefined 新字段的场景。
 */
export function withCompatibility<T extends Record<string, unknown>>(
  data: T | null | undefined,
  defaults: Partial<T>,
): T {
  if (!data) return { ...defaults } as T;
  const result = { ...defaults, ...data } as Record<string, unknown>;
  // null 值用默认值覆盖
  for (const key of Object.keys(defaults)) {
    if (result[key] === null || result[key] === undefined) {
      result[key] = defaults[key as keyof typeof defaults];
    }
  }
  return result as T;
}

// ─── 废弃字段监控 ──────────────────────────────────────

/**
 * 废弃字段检测器。
 * 在请求拦截器中调用，捕获后端返回的废弃字段并上报。
 */
export function detectDeprecatedFields(
  data: Record<string, unknown>,
  deprecatedFields: string[],
): { field: string; value: unknown }[] {
  const hits: { field: string; value: unknown }[] = [];
  for (const field of deprecatedFields) {
    if (field in data && data[field] !== null && data[field] !== undefined) {
      hits.push({ field, value: data[field] });
    }
  }
  if (hits.length > 0) {
    console.warn('[SchemaCompatibility] Deprecated fields detected:', hits);
    // TODO: 上报埋点
  }
  return hits;
}

// ─── Schema 修改记录类型 ──────────────────────────────

export interface SchemaChangeLogItem {
  id: number;
  schemaName: string;
  fieldPath: string;
  changeType: 'ADD' | 'CREATE' | 'MODIFY' | 'DEPRECATE' | 'FREEZE' | 'UNFREEZE' | 'DELETE';
  oldValue: string | null;
  newValue: string | null;
  operator: string | null;
  reason: string | null;
  createdAt: string;
}

/** ChangeLog 变更类型的中文标签 */
export const CHANGE_TYPE_LABELS: Record<string, string> = {
  ADD: '新增字段',
  CREATE: '创建',
  MODIFY: '修改',
  DEPRECATE: '废弃',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  DELETE: '删除',
};

/** ChangeLog 变更类型的颜色映射 */
export const CHANGE_TYPE_COLORS: Record<string, string> = {
  ADD: 'green',
  CREATE: 'green',
  MODIFY: 'blue',
  DEPRECATE: 'orange',
  FREEZE: 'cyan',
  UNFREEZE: 'purple',
  DELETE: 'red',
};
