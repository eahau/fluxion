// Entities/Schema API —— 实体级 CRUD + datasources/tables/columns 与 schema 域强相关的"基础服务"
import { apiGet, apiPost, apiPut, apiDelete, RequestOptions } from '@/shared/api/request';
import type { SchemaDefinition } from './model/types';

export async function listSchemas(
  params?: { keyword?: string; schemaType?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return apiGet<API.PageResponse<SchemaDefinition> | SchemaDefinition[]>(
    '/api/admin/schemas',
    { ...params, pageSize: params?.pageSize ?? 50, page: params?.page ?? 1 },
    options,
  );
}

export async function getSchema(
  identifier: string,
  options?: RequestOptions,
) {
  const params: Record<string, any> = { name: identifier };
  const byName = await apiGet<SchemaDefinition[] | any>('/api/admin/schemas', params, options).catch(
    () => null as any,
  );
  if (Array.isArray(byName) && byName.length > 0) return byName[0] as SchemaDefinition;
  if (byName && !Array.isArray(byName) && (byName.list || byName.items)) {
    const arr = byName.list || byName.items;
    if (Array.isArray(arr) && arr.length > 0) return arr[0] as SchemaDefinition;
  }
  // name 没命中 → 当 id 查
  return apiGet<SchemaDefinition>(`/api/admin/schemas/${identifier}`, undefined, options);
}

export async function createSchema(
  data: Partial<SchemaDefinition> & { name: string; schema?: any },
  options?: RequestOptions,
) {
  return apiPost<SchemaDefinition>('/api/admin/schemas', data, options);
}

export async function updateSchema(
  id: string,
  data: Partial<SchemaDefinition>,
  options?: RequestOptions,
) {
  return apiPut<SchemaDefinition>(`/api/admin/schemas/${id}`, data, options);
}

export async function deleteSchema(id: string, options?: RequestOptions) {
  return apiDelete<void>(`/api/admin/schemas/${id}`, options);
}

// ---------- Schema 域关联的基础服务（与 schema 编辑/预览强相关） ----------
export async function listDatasources(options?: RequestOptions) {
  const data: any = await apiGet<any>('/api/admin/schemas/datasources', undefined, options);
  return Array.isArray(data) ? (data as string[]) : ((data?.list ?? data?.items ?? []) as string[]);
}

export async function listTables(
  dataSource: string,
  options?: RequestOptions,
): Promise<string[]> {
  const data: any = await apiGet<any>('/api/admin/schemas/tables', { dataSource }, options);
  const arr = Array.isArray(data) ? data : data?.list ?? data?.items ?? [];
  return arr.map((x: any) => (typeof x === 'string' ? x : x.tableName ?? x.name));
}

export async function listTableColumns(
  tableName: string,
  dataSource: string,
  options?: RequestOptions,
): Promise<Array<{ name: string; dataType?: string; comment?: string }>> {
  const res: any = await apiGet<any>(
    '/api/admin/schemas/columns',
    { table: tableName, dataSource },
    options,
  );
  const arr = Array.isArray(res) ? res : res?.list ?? res?.items ?? [];
  return arr.map((c: any) => ({
    name: c.name ?? c.columnName,
    dataType: c.type ?? c.dataType ?? c.columnType,
    comment: c.comment ?? c.columnComment,
  }));
}
