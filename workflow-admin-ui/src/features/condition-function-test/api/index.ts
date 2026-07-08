// features/condition-function-test/api/index.ts
// === Condition/Filter Feature 专属 API ===
// 内嵌条件函数测试所需的端点（Schema 列表/样本 + 函数测试）

import { apiPost } from '@/shared/api/request';
import {
  getSchemas as serviceGetSchemas,
} from '@/services/schema';
import { getSchema as entityGetSchemaByName } from '@/entities/schema/api';
import type { SchemaObject } from '../lib/utils';
import { generateSampleFromSchema } from '../lib/utils';

export type SchemaBrief = Array<{
  id?: string; schemaName?: string; name?: string;
  description?: string; schemaType?: string;
}>;

export async function listSchemas(opts?: { keyword?: string; pageSize?: number }): Promise<SchemaBrief> {
  try {
    const params: Record<string, any> = { keyword: opts?.keyword ?? '' };
    if (opts?.pageSize) {
      params.pageSize = opts.pageSize;
      params.page = 1;
    }
    const res = await serviceGetSchemas(params);
    const arr: any[] = Array.isArray(res)
      ? res
      : (res as any)?.list
      ?? (res as any)?.items
      ?? (res as any)?.data?.list
      ?? (res as any)?.data?.items
      ?? (res as any)?.data
      ?? [];
    const finalArr: any[] = Array.isArray(arr) ? arr : [];
    console.warn('[condition-test:listSchemas] 原始响应:', res, '提取数组:', finalArr.length, '类型:', typeof res, 'resKeys:', res && typeof res === 'object' ? Object.keys(res) : [], 'params:', params);
    return finalArr as SchemaBrief;
  } catch (e: any) {
    console.error('[condition-test:listSchemas] 加载 Schema 列表失败:', e?.message ?? e);
    return [] as SchemaBrief;
  }
}

export async function getSchemaObjectByName(name: string): Promise<SchemaObject | null> {
  const row = await entityGetSchemaByName(name);
  if (!row) return null;
  // schemaJson: 兼容 API 返回 string（JSON 字符串）或已经解析的 object
  const raw = (row as any)?.schemaJson ?? (row as any)?.schema;
  if (!raw) return null;
  if (typeof raw === 'string') {
    try { return JSON.parse(raw); } catch { return null; }
  }
  return raw as SchemaObject;
}

export { generateSampleFromSchema };

export async function testConditionFunction(
  functionId: string,
  payload: { inputs: Record<string, any> },
): Promise<any> {
  return apiPost(`/api/admin/functions/${encodeURIComponent(functionId)}/test`, payload);
}
