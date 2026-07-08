// features/db-function-test/api/index.ts
// === DB Function Test Feature 专属 API ===
// 仅此 Feature 需要的后端端点：数据源列举、表结构、表列、函数测试、SQL 历史

import { apiPost, apiGet } from '@/shared/api/request';
import { getSqlHistory as readSqlHist, saveSqlHistory as writeSqlHist } from '@/shared/lib/sql-history';
import type { SqlHistoryEntry } from '@/shared/types/function-test-common';

// —— 1. DB 领域专属元数据 API（和实体级 schema CRUD 分离，只给 DB 测试用）——
export interface DbDatasourceBrief {
  id?: string;
  name?: string;
  domain?: 'db';
  type?: string;
}
export interface DbTableColumn {
  name: string;
  dataType?: string;
  comment?: string;
}

/**
 * 列举 DB 数据源（应用/连接）
 */
export async function listDatasources(): Promise<DbDatasourceBrief[]> {
  try {
    const res = await apiGet<any>('/api/admin/datasources');
    const arr: any[] = Array.isArray(res) ? res : (res as any)?.list ?? (res as any)?.items ?? (res as any)?.data ?? [];
    return arr.map((o) => (typeof o === 'string' ? { id: o, name: o, domain: 'db' } : { id: o.id ?? o.name, name: o.name ?? o.id, domain: o.domain ?? 'db', type: o.type }));
  } catch (e) {
    return [];
  }
}

/**
 * 列举指定数据源的数据表
 */
export async function listTables(datasourceId: string): Promise<string[]> {
  try {
    const res = await apiGet<any>(`/api/admin/datasources/${encodeURIComponent(datasourceId)}/tables`);
    const arr: any[] = Array.isArray(res) ? res : (res as any)?.list ?? (res as any)?.items ?? (res as any)?.tables ?? (res as any)?.data ?? [];
    return arr.map((o) => (typeof o === 'string' ? o : o.tableName ?? o.name ?? String(o)));
  } catch (e) {
    return [];
  }
}

/**
 * 列举指定数据表的列结构
 */
export async function listTableColumns(tableName: string, datasourceId: string): Promise<DbTableColumn[]> {
  try {
    const res = await apiGet<any>(
      `/api/admin/datasources/${encodeURIComponent(datasourceId)}/tables/${encodeURIComponent(tableName)}/columns`,
    );
    const arr: any[] = Array.isArray(res) ? res : (res as any)?.list ?? (res as any)?.columns ?? (res as any)?.fields ?? (res as any)?.data ?? [];
    return arr.map((o) => ({
      name: o.name ?? o.columnName ?? o.field ?? String(o),
      dataType: o.dataType ?? o.type,
      comment: o.comment ?? o.description,
    }));
  } catch (e) {
    return [];
  }
}

// —— 2. 执行 DB 函数测试（Feature 专属 HTTP 调用）——
export interface TestDbFunctionInput {
  inputs: Record<string, any>;
}

export async function testDbFunction(functionId: string, payload: TestDbFunctionInput): Promise<any> {
  return apiPost(`/api/admin/functions/${encodeURIComponent(functionId)}/test`, payload);
}

// —— 3. SQL 历史（localStorage，和其他 features 完全解耦）——
export function getSqlHistory(functionId: string): SqlHistoryEntry[] {
  return readSqlHist(functionId);
}
export function saveSqlHistory(
  functionId: string,
  entry: Omit<SqlHistoryEntry, 'truncated'> & { truncated?: boolean },
): SqlHistoryEntry[] {
  return writeSqlHist(functionId, entry);
}
