import type { SchemaDefinition } from '@/types/api';

/**
 * Schema 过滤规则（函数测试 UI 专用）
 * ———————————————————————————————————————————————————
 * 规则 1：名称以 `builtin:` 开头的系统 Schema → 无条件过滤（如 builtin:Execute:output / builtin:redis:command:param）
 * 规则 2：scope=PLATFORM 且 category=FROZEN 的冻结平台 Schema → 不过滤（用户自定义冻结的平台级 Schema 可能被拿来当测试输入）
 * 规则 3：schemaName 为 undefined / null / 空字符串 → 保留（容错）
 */

export const BUILTIN_SCHEMA_PREFIX = 'builtin:';

export function isBuiltinSchema(schema: SchemaDefinition | null | undefined): boolean {
  if (!schema) return false;
  const name = (schema.schemaName ?? schema.name ?? '').toString().trim();
  return name.startsWith(BUILTIN_SCHEMA_PREFIX);
}

export function filterOutBuiltinSchemas<T extends SchemaDefinition>(schemas: T[] | null | undefined): T[] {
  if (!Array.isArray(schemas) || schemas.length === 0) return [];
  return schemas.filter((s) => !isBuiltinSchema(s));
}
