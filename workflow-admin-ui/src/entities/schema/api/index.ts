import { client, unwrap, silentHeaders, type RequestOptions } from '@/sdk';
import type { SchemaDefinition } from './model/types';

export async function listSchemas(
  params?: { keyword?: string; schemaType?: string; page?: number; pageSize?: number },
  options?: RequestOptions,
) {
  return unwrap(
    await client.GET('/api/admin/schemas', {
      params: {
        query: { ...params, pageSize: params?.pageSize ?? 50, page: params?.page ?? 1 } as any,
      },
      headers: silentHeaders(options),
    }),
  ) as API.PageResponse<SchemaDefinition> | SchemaDefinition[];
}

export async function getSchema(
  identifier: string,
  options?: RequestOptions,
): Promise<SchemaDefinition> {
  const byName = await client.GET('/api/admin/schemas', {
    params: { query: { name: identifier } as any },
    headers: silentHeaders(options),
  })
    .then((r) => unwrap(r))
    .catch(() => null as any);

  if (Array.isArray(byName) && byName.length > 0) return byName[0] as SchemaDefinition;
  if (byName && !Array.isArray(byName) && (byName.list || byName.items)) {
    const arr = byName.list || byName.items;
    if (Array.isArray(arr) && arr.length > 0) return arr[0] as SchemaDefinition;
  }
  return unwrap(
    await client.GET('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: identifier } },
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
}

export async function createSchema(
  data: Partial<SchemaDefinition> & { name: string; schema?: any },
  options?: RequestOptions,
) {
  return unwrap(
    await client.POST('/api/admin/schemas', {
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
}

export async function updateSchema(
  id: string,
  data: Partial<SchemaDefinition>,
  options?: RequestOptions,
) {
  return unwrap(
    await client.PUT('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: id } },
      body: data as any,
      headers: silentHeaders(options),
    }),
  ) as SchemaDefinition;
}

export async function deleteSchema(id: string, options?: RequestOptions) {
  unwrap(
    await client.DELETE('/api/admin/schemas/{schemaName}', {
      params: { path: { schemaName: id } },
      headers: silentHeaders(options),
    }),
  );
  return undefined as void;
}

export async function listDatasources(options?: RequestOptions): Promise<string[]> {
  const data: any = unwrap(
    await client.GET('/api/datasources', { headers: silentHeaders(options) }),
  );
  return Array.isArray(data) ? (data as string[]) : ((data?.list ?? data?.items ?? []) as string[]);
}

export async function listTables(
  dataSource: string,
  options?: RequestOptions,
): Promise<string[]> {
  const data: any = unwrap(
    await client.GET('/api/admin/schemas/tables', {
      params: { query: { dataSource } },
      headers: silentHeaders(options),
    }),
  );
  const arr = Array.isArray(data) ? data : data?.list ?? data?.items ?? [];
  return arr.map((x: any) => (typeof x === 'string' ? x : x.tableName ?? x.name));
}

export async function listTableColumns(
  tableName: string,
  dataSource: string,
  options?: RequestOptions,
): Promise<Array<{ name: string; dataType?: string; comment?: string }>> {
  const res: any = unwrap(
    await client.GET('/api/admin/schemas/tables/{tableName}/columns', {
      params: { path: { tableName }, query: { dataSource } },
      headers: silentHeaders(options),
    }),
  );
  const arr = Array.isArray(res) ? res : res?.list ?? res?.items ?? [];
  return arr.map((c: any) => ({
    name: c.name ?? c.columnName,
    dataType: c.type ?? c.dataType ?? c.columnType,
    comment: c.comment ?? c.columnComment,
  }));
}
