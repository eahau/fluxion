// features/db-function-test/lib/utils.ts
// DB Feature 专属纯函数（目前公共逻辑已经在 shared/lib/*，这里仅做特征检测）

import type { SchemaObject } from '@/shared/lib/json-schema';
import { hasSqlField as baseHasSqlField } from '@/shared/lib/json-schema';

export const hasSqlField = (schema: SchemaObject | null): boolean => baseHasSqlField(schema);

export const DB_DOMAIN_KEYS = new Set(['db', 'database', 'rdb', 'sql']);
export function isDbDomain(domain?: string, schema?: SchemaObject | null): boolean {
  if (domain && DB_DOMAIN_KEYS.has(String(domain).toLowerCase())) return true;
  return !!schema && hasSqlField(schema);
}

/**
 * 将 entity api 返回的 datasource 统一成下拉选项格式（db feature 自用）
 */
export function toDatasourceOptions(arr: Array<string | Record<string, any>>): Array<{ value: string; label: string }> {
  const out: Array<{ value: string; label: string }> = [];
  for (const ds of arr) {
    if (typeof ds === 'string') out.push({ value: ds, label: ds });
    else if (ds && typeof ds === 'object') {
      const v = ds.id ?? ds.name;
      if (!v) continue;
      out.push({ value: v, label: ds.name ?? v });
    }
  }
  return out;
}
